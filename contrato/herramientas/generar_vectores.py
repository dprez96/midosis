"""
Genera los vectores de prueba de la carga útil, de la cobertura del tramo y de
la pila completa del código (firma, compresión, cifrado y Base45).

Es una implementación de referencia, escrita aparte de la de Kotlin a propósito:
si core, paciente y el motor se probaran solo contra vectores producidos por su
propio código, un error compartido pasaría inadvertido.

Cada caso declara a mano el resultado que espera. El generador lo comprueba
contra el esquema (carga-v1.cddl), las reglas de carga-v1.md y la lectura de
pila-v1.md antes de escribir nada: si no coinciden, se detiene.

Uso, desde la raíz del repositorio:

    python contrato/herramientas/generar_vectores.py              escribe los vectores
    python contrato/herramientas/generar_vectores.py --comprobar  falla si no están al día
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import sys
import unicodedata
from datetime import date, timedelta
from pathlib import Path

import cbor2
import pycddl
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

import pila
from pila import ClaveDeContenido, ClaveDeFirma, Rechazo

CONTRATO = Path(__file__).resolve().parent.parent
ESQUEMA = CONTRATO / "esquema" / "carga-v1.cddl"
VECTORES = CONTRATO / "vectores"

VIGENCIA_MAXIMA_S = 72 * 3600  # RN-03
INTERVALO_MINIMO_MIN = 30
INTERVALO_MAXIMO_MIN = 90 * 24 * 60
EN_AYUNAS, CON_ALIMENTOS = 1, 2

# Motivos de rechazo. Son los mismos nombres en todos los módulos.
ESTRUCTURA = "estructura-invalida"
VERSION = "version-no-soportada"
CONTENIDO = "contenido-incoherente"


# ---------------------------------------------------------------------------
# Datos sintéticos
# ---------------------------------------------------------------------------

def gtin(sin_digito: str) -> str:
    """Agrega el dígito verificador GS1: pesos 3 y 1 alternados desde la derecha."""
    suma = sum(int(c) * (3 if i % 2 == 0 else 1) for i, c in enumerate(reversed(sin_digito)))
    return sin_digito + str((10 - suma % 10) % 10)


CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"


def ulid(instante_s: int, semilla: str) -> str:
    """ULID con la marca de tiempo de la emisión y una parte aleatoria fija por caso."""
    aleatorio = int.from_bytes(hashlib.sha256(semilla.encode()).digest()[:10], "big")
    valor = (instante_s * 1000) << 80 | aleatorio
    return "".join(CROCKFORD[(valor >> (5 * i)) & 31] for i in reversed(range(26)))


# Instantes del ejemplo del informe (sección 7.5.1): 17/08/2026 y 72 horas después.
IAT = 1786982400
EXP = IAT + VIGENCIA_MAXIMA_S

LOSARTAN = gtin("780225001234")
METFORMINA = gtin("780225001235")
ATORVASTATINA = gtin("780225001236")
BUDESONIDA = gtin("780225001237")
INSULINA = gtin("780225001238")


def losartan(**cambios) -> dict:
    producto = {
        "c": LOSARTAN,
        "n": "Losartán 50 mg comp.",
        "a": "Losartán",
        "d": "1 comprimido",
        "f": "PT12H",
        "du": 180,
        "e": 60,
        "co": 30,
        "r": "VO",
        "o": [CON_ALIMENTOS],
        "nt": "No suspender sin indicación médica",
    }
    producto.update(cambios)
    return {k: v for k, v in producto.items() if v is not None}


def carga(*productos: dict, semilla: str = "losartan", **cambios) -> dict:
    raiz = {
        "v": 1,
        "cm": "13123",
        "iss": "CL-FP-13123-01",
        "jti": ulid(IAT, semilla),
        "iat": IAT,
        "exp": EXP,
        "rx": list(productos) or [losartan()],
    }
    raiz.update(cambios)
    return {k: v for k, v in raiz.items() if v is not None}


def sin(clave: str, d: dict) -> dict:
    return {k: v for k, v in d.items() if k != clave}


# ---------------------------------------------------------------------------
# Referencia: cómo debe leer la carga cualquier módulo
# ---------------------------------------------------------------------------

def _claves_repetidas(datos: bytes) -> bool:
    """Recorre el CBOR y dice si algún mapa repite una clave. cbor2 se queda con la última."""
    flujo = io.BytesIO(datos)

    def largo(info: int) -> int:
        if info < 24:
            return info
        if info > 27:
            raise NotImplementedError("los vectores no usan longitudes indefinidas")
        return int.from_bytes(flujo.read(1 << (info - 24)), "big")

    def item() -> tuple[bool, bytes]:
        inicio = flujo.tell()
        inicial = flujo.read(1)[0]
        mayor, info = inicial >> 5, inicial & 31
        repetida = False
        if mayor in (0, 1):
            largo(info)
        elif mayor in (2, 3):
            flujo.read(largo(info))
        elif mayor == 4:
            for _ in range(largo(info)):
                repetida |= item()[0]
        elif mayor == 5:
            vistas = set()
            for _ in range(largo(info)):
                rep_clave, clave = item()
                rep_valor, _ = item()
                repetida |= rep_clave or rep_valor or clave in vistas
                vistas.add(clave)
        elif mayor == 6:
            repetida = item()[0]
        else:
            largo(info)
        fin = flujo.tell()
        flujo.seek(inicio)
        return repetida, flujo.read(fin - inicio)

    return item()[0]


def leer(datos: bytes, esquema: pycddl.Schema) -> dict:
    """Decodifica y valida una carga. Lanza Rechazo con el motivo y la regla."""
    flujo = io.BytesIO(datos)
    try:
        valor = cbor2.CBORDecoder(flujo).decode()
    except Exception:
        raise Rechazo(ESTRUCTURA, "cbor-mal-formado")
    if flujo.tell() != len(datos) or _claves_repetidas(datos):
        raise Rechazo(ESTRUCTURA, "cbor-mal-formado")

    # La versión se mira antes que el resto: un código de una versión futura no
    # es un código dañado, y la aplicación debe pedir que se actualice.
    if not isinstance(valor, dict):
        raise Rechazo(ESTRUCTURA, "no-es-mapa")
    v = valor.get("v")
    if not isinstance(v, int) or isinstance(v, bool) or v < 0:
        raise Rechazo(ESTRUCTURA, "sin-version")
    if v != 1:
        raise Rechazo(VERSION, "version")

    try:
        esquema.validate_cbor(datos)
    except pycddl.ValidationError:
        raise Rechazo(ESTRUCTURA, "esquema")

    _revisar_contenido(valor)
    return valor


def minutos(intervalo: str) -> int:
    """PT1H30M -> 90. El esquema ya garantizó la forma canónica."""
    resto = intervalo[2:]
    horas = mins = 0
    if "H" in resto:
        h, resto = resto.split("H")
        horas = int(h)
    if resto:
        mins = int(resto.rstrip("M"))
    return horas * 60 + mins


def cobertura_dias(e: int, intervalo: str) -> int:
    """Días completos que cubren las dosis entregadas, redondeando hacia abajo."""
    return e * minutos(intervalo) // (24 * 60)


def _sin_control(texto: str) -> bool:
    return all(unicodedata.category(c) != "Cc" for c in texto)


def _revisar_contenido(c: dict) -> None:
    if c["iss"][6:11] != c["cm"]:
        raise Rechazo(CONTENIDO, "comuna-del-emisor")
    if not c["iat"] < c["exp"] <= c["iat"] + VIGENCIA_MAXIMA_S:
        raise Rechazo(CONTENIDO, "vigencia")
    if c.get("rp") == c["jti"]:
        raise Rechazo(CONTENIDO, "reemplazo-distinto")
    for p in c["rx"]:
        if gtin(p["c"][:-1]) != p["c"]:
            raise Rechazo(CONTENIDO, "gtin-digito-verificador")
        if not INTERVALO_MINIMO_MIN <= minutos(p["f"]) <= INTERVALO_MAXIMO_MIN:
            raise Rechazo(CONTENIDO, "intervalo-en-rango")
        if p["co"] != cobertura_dias(p["e"], p["f"]):
            raise Rechazo(CONTENIDO, "cobertura")
        o = p.get("o", [])
        if any(a >= b for a, b in zip(o, o[1:])):
            raise Rechazo(CONTENIDO, "observaciones-ordenadas")
        if EN_AYUNAS in o and CON_ALIMENTOS in o:
            raise Rechazo(CONTENIDO, "observaciones-compatibles")
        if not all(_sin_control(p[k]) for k in ("n", "a", "d", "nt") if k in p):
            raise Rechazo(CONTENIDO, "sin-caracteres-de-control")


# ---------------------------------------------------------------------------
# Casos de la carga útil
# ---------------------------------------------------------------------------

def cbor(valor) -> bytes:
    """CBOR determinista (RFC 8949, sección 4.2.1)."""
    return cbor2.dumps(valor, canonical=True)


def _plan_maximo() -> dict:
    productos = []
    for i in range(20):
        productos.append({
            "c": gtin(f"7802250099{i:02d}"),
            "n": f"Producto sintético {i + 1:02d}",
            "a": f"Principio activo {i + 1:02d}",
            "d": "1 comprimido",
            "f": "PT24H",
            "du": 30,
            "e": 30,
            "co": 30,
            "r": "VO",
        })
    return carga(*productos, semilla="plan-maximo")


def _datos_sobrantes() -> bytes:
    return cbor(carga()) + b"\x00"


def _clave_repetida() -> bytes:
    # Un mapa de 8 pares cuya última clave vuelve a ser «v».
    base = cbor(carga())
    return bytes([base[0] + 1]) + base[1:] + cbor("v") + cbor(1)


# Exactamente 240 bytes en UTF-8, aunque son menos caracteres: cada letra con tilde ocupa dos.
TEXTO_240 = ("Tomar con un vaso con agua. Si olvida una dosis, tómela apenas lo recuerde, "
             "salvo que falte poco para la siguiente. No duplique la dosis. Consulte a "
             "su químico farmacéutico ante cualquier reacción adversa o duda sobre el uso. Guárdelo.")
assert len(TEXTO_240.encode()) == 240

CASOS = [
    # --- Válidos -----------------------------------------------------------
    dict(
        nombre="valido-losartan-fraccionado",
        descripcion="El ejemplo del informe (sección 7.5.1): 180 días indicados, 60 dosis cada 12 horas, que cubren 30 días.",
        carga=carga(),
        resultado={"valido": True},
    ),
    dict(
        nombre="valido-varios-productos",
        descripcion="Tres productos con vías y observaciones distintas. En la budesonida, «e» cuenta tomas de dos inhalaciones, no inhalaciones.",
        carga=carga(
            {"c": METFORMINA, "n": "Metformina 850 mg comp.", "a": "Metformina", "d": "1 comprimido",
             "f": "PT12H", "du": 90, "e": 60, "co": 30, "r": "VO", "o": [CON_ALIMENTOS]},
            {"c": ATORVASTATINA, "n": "Atorvastatina 20 mg comp.", "a": "Atorvastatina", "d": "1 comprimido",
             "f": "PT24H", "du": 90, "e": 30, "co": 30, "r": "VO", "o": [3]},
            {"c": BUDESONIDA, "n": "Budesonida 200 mcg inh.", "a": "Budesonida", "d": "2 inhalaciones",
             "f": "PT12H", "du": 60, "e": 100, "co": 50, "r": "INH"},
            semilla="varios-productos",
        ),
        resultado={"valido": True},
    ),
    dict(
        nombre="valido-minimo",
        descripcion="Solo las claves obligatorias: sin observaciones ni nota.",
        carga=carga(
            {"c": INSULINA, "n": "Insulina NPH 100 UI/ml", "a": "Insulina humana isófana", "d": "10 UI",
             "f": "PT12H", "du": 30, "e": 60, "co": 30, "r": "SC"},
            semilla="minimo",
        ),
        resultado={"valido": True},
    ),
    dict(
        nombre="valido-codigo-de-correccion",
        descripcion="Código de reemplazo (RN-25): «rp» apunta al tratamiento del vector valido-losartan-fraccionado.",
        carga=carga(losartan(f="PT24H", co=60), semilla="correccion", rp=ulid(IAT, "losartan")),
        resultado={"valido": True},
    ),
    dict(
        nombre="limite-cobertura-menor-a-un-dia",
        descripcion="Una sola dosis cada 12 horas no completa un día: la cobertura es 0.",
        carga=carga(losartan(du=1, e=1, co=0, o=None, nt=None), semilla="cobertura-cero"),
        resultado={"valido": True},
    ),
    dict(
        nombre="limite-valores-extremos",
        descripcion="Textos en su largo máximo en bytes (con tildes, que ocupan dos), cuatro observaciones y los intervalos mínimo y máximo.",
        carga=carga(
            losartan(n="Hidroclorotiazida + losartán 12,5/50 mg",
                     a="Hidroclorotiazida y losartán potásico en combinación fija",
                     d="Medio comprimido, partido por la ranura.", o=[2, 3, 4, 5], nt=TEXTO_240, du=365, e=4,
                     f="PT2160H", co=360),
            losartan(e=9999, f="PT30M", co=208, du=365),
            semilla="valores-extremos",
        ),
        resultado={"valido": True},
    ),
    dict(
        nombre="limite-plan-maximo",
        descripcion="Veinte productos, el máximo del esquema. No cabe en un QR impreso: se entrega por código corto.",
        carga=_plan_maximo(),
        resultado={"valido": True},
    ),

    # --- Estructura y versión ---------------------------------------------
    dict(
        nombre="invalido-version-futura",
        descripcion="Una versión que esta aplicación no conoce: se pide actualizarla, no se trata como código dañado.",
        carga=carga(v=2),
        resultado={"valido": False, "motivo": VERSION, "regla": "version"},
    ),
    dict(
        nombre="invalido-sin-version",
        descripcion="Falta la clave «v».",
        carga=sin("v", carga()),
        resultado={"valido": False, "motivo": ESTRUCTURA, "regla": "sin-version"},
    ),
    dict(
        nombre="invalido-no-es-mapa",
        descripcion="La raíz es un arreglo y no un mapa.",
        carga=[1, "13123"],
        resultado={"valido": False, "motivo": ESTRUCTURA, "regla": "no-es-mapa"},
    ),
    dict(
        nombre="invalido-cbor-truncado",
        descripcion="Los bytes terminan a mitad de la estructura.",
        bytes_=cbor(carga())[:-5],
        resultado={"valido": False, "motivo": ESTRUCTURA, "regla": "cbor-mal-formado"},
    ),
    dict(
        nombre="invalido-datos-sobrantes",
        descripcion="Una carga válida seguida de un byte de más: se lee un solo elemento y nada después.",
        bytes_=_datos_sobrantes(),
        resultado={"valido": False, "motivo": ESTRUCTURA, "regla": "cbor-mal-formado"},
    ),
    dict(
        nombre="invalido-clave-repetida",
        descripcion="La clave «v» aparece dos veces. Hay lectores que se quedan con la primera y otros con la última.",
        bytes_=_clave_repetida(),
        resultado={"valido": False, "motivo": ESTRUCTURA, "regla": "cbor-mal-formado"},
    ),
    dict(
        nombre="invalido-clave-desconocida",
        descripcion="Una clave que el esquema no define. Los mapas son cerrados: así no viaja ningún dato del paciente (RNF-13).",
        carga=carga(nom="Paciente Sintético"),
        resultado={"valido": False, "motivo": ESTRUCTURA, "regla": "esquema"},
    ),
    dict(
        nombre="invalido-falta-cobertura",
        descripcion="Un producto sin «co» (ADR-010: los tres valores son obligatorios).",
        carga=carga(sin("co", losartan())),
        resultado={"valido": False, "motivo": ESTRUCTURA, "regla": "esquema"},
    ),
    dict(
        nombre="invalido-sin-productos",
        descripcion="«rx» vacío.",
        carga=carga(rx=[]),
        resultado={"valido": False, "motivo": ESTRUCTURA, "regla": "esquema"},
    ),
    dict(
        nombre="invalido-intervalo-no-canonico",
        descripcion="P1D es una duración ISO 8601 válida, pero el contrato exige la forma canónica PT24H.",
        carga=carga(losartan(f="P1D", co=60)),
        resultado={"valido": False, "motivo": ESTRUCTURA, "regla": "esquema"},
    ),
    dict(
        nombre="invalido-duracion-como-texto",
        descripcion="«du» llega como texto y no como entero.",
        carga=carga(losartan(du="180")),
        resultado={"valido": False, "motivo": ESTRUCTURA, "regla": "esquema"},
    ),
    dict(
        nombre="invalido-via-desconocida",
        descripcion="La vía endovenosa no se dispensa en la farmacia y no está en la lista.",
        carga=carga(losartan(r="EV")),
        resultado={"valido": False, "motivo": ESTRUCTURA, "regla": "esquema"},
    ),
    dict(
        nombre="invalido-nota-demasiado-larga",
        descripcion="241 bytes en «nt»: el límite se cuenta en bytes UTF-8.",
        carga=carga(losartan(nt=TEXTO_240 + ".")),
        resultado={"valido": False, "motivo": ESTRUCTURA, "regla": "esquema"},
    ),

    # --- Contenido ---------------------------------------------------------
    dict(
        nombre="invalido-gtin-digito-verificador",
        descripcion="7802250012345: el dígito verificador correcto es 4.",
        carga=carga(losartan(c="7802250012345")),
        resultado={"valido": False, "motivo": CONTENIDO, "regla": "gtin-digito-verificador"},
    ),
    dict(
        nombre="invalido-cobertura-incoherente",
        descripcion="60 dosis cada 12 horas cubren 30 días, no 31.",
        carga=carga(losartan(co=31)),
        resultado={"valido": False, "motivo": CONTENIDO, "regla": "cobertura"},
    ),
    dict(
        nombre="invalido-vigencia-mayor-a-72-horas",
        descripcion="«exp» un segundo después del máximo de RN-03.",
        carga=carga(exp=IAT + VIGENCIA_MAXIMA_S + 1),
        resultado={"valido": False, "motivo": CONTENIDO, "regla": "vigencia"},
    ),
    dict(
        nombre="invalido-expira-al-emitirse",
        descripcion="«exp» igual a «iat»: el código nunca estuvo vigente.",
        carga=carga(exp=IAT),
        resultado={"valido": False, "motivo": CONTENIDO, "regla": "vigencia"},
    ),
    dict(
        nombre="invalido-comuna-distinta-del-emisor",
        descripcion="El punto de dispensación es de la comuna 13101 y la carga declara la 13123.",
        carga=carga(iss="CL-FP-13101-01"),
        resultado={"valido": False, "motivo": CONTENIDO, "regla": "comuna-del-emisor"},
    ),
    dict(
        nombre="invalido-intervalo-menor-al-minimo",
        descripcion="Cada 20 minutos: bajo el mínimo de 30 que admite el motor.",
        carga=carga(losartan(f="PT20M", co=0)),
        resultado={"valido": False, "motivo": CONTENIDO, "regla": "intervalo-en-rango"},
    ),
    dict(
        nombre="invalido-intervalo-mayor-al-maximo",
        descripcion="Una hora más que 90 días.",
        carga=carga(losartan(f="PT2161H", e=1, co=90)),
        resultado={"valido": False, "motivo": CONTENIDO, "regla": "intervalo-en-rango"},
    ),
    dict(
        nombre="invalido-reemplaza-a-si-mismo",
        descripcion="«rp» igual a «jti».",
        carga=carga(rp=ulid(IAT, "losartan")),
        resultado={"valido": False, "motivo": CONTENIDO, "regla": "reemplazo-distinto"},
    ),
    dict(
        nombre="invalido-observaciones-desordenadas",
        descripcion="Las observaciones van en orden ascendente y sin repetir.",
        carga=carga(losartan(o=[3, 2])),
        resultado={"valido": False, "motivo": CONTENIDO, "regla": "observaciones-ordenadas"},
    ),
    dict(
        nombre="invalido-observaciones-incompatibles",
        descripcion="En ayunas y con alimentos a la vez.",
        carga=carga(losartan(o=[1, 2])),
        resultado={"valido": False, "motivo": CONTENIDO, "regla": "observaciones-compatibles"},
    ),
    dict(
        nombre="invalido-caracter-de-control",
        descripcion="Un salto de línea en la nota. Core limpia los caracteres de control antes de emitir.",
        carga=carga(losartan(nt="No suspender\nsin indicación médica")),
        resultado={"valido": False, "motivo": CONTENIDO, "regla": "sin-caracteres-de-control"},
    ),
]


# ---------------------------------------------------------------------------
# Casos de cobertura (ADR-010)
# ---------------------------------------------------------------------------

COBERTURA = [
    dict(nombre="losartan-del-informe", du=180, e=60, f="PT12H", retiro="2026-08-17",
         esperado=dict(co=30, agotamiento="2026-09-16", completaLoIndicado=False, diasPendientes=150, excedeLoIndicado=False)),
    dict(nombre="uno-diario-de-la-hu-04", du=180, e=60, f="PT24H", retiro="2026-10-15",
         esperado=dict(co=60, agotamiento="2026-12-14", completaLoIndicado=False, diasPendientes=120, excedeLoIndicado=False)),
    dict(nombre="completa-justo-lo-indicado", du=30, e=90, f="PT8H", retiro="2026-10-15",
         esperado=dict(co=30, agotamiento="2026-11-14", completaLoIndicado=True, diasPendientes=0, excedeLoIndicado=False)),
    dict(nombre="entrega-de-mas", du=7, e=30, f="PT24H", retiro="2026-10-15",
         esperado=dict(co=30, agotamiento="2026-11-14", completaLoIndicado=True, diasPendientes=0, excedeLoIndicado=True)),
    dict(nombre="redondea-hacia-abajo", du=10, e=20, f="PT8H", retiro="2026-10-15",
         esperado=dict(co=6, agotamiento="2026-10-21", completaLoIndicado=False, diasPendientes=4, excedeLoIndicado=False)),
    dict(nombre="menos-de-un-dia", du=1, e=1, f="PT12H", retiro="2026-10-15",
         esperado=dict(co=0, agotamiento="2026-10-15", completaLoIndicado=False, diasPendientes=1, excedeLoIndicado=False)),
    dict(nombre="intervalo-con-minutos", du=3, e=16, f="PT1H30M", retiro="2026-10-15",
         esperado=dict(co=1, agotamiento="2026-10-16", completaLoIndicado=False, diasPendientes=2, excedeLoIndicado=False)),
    dict(nombre="semanal", du=84, e=4, f="PT168H", retiro="2026-10-15",
         esperado=dict(co=28, agotamiento="2026-11-12", completaLoIndicado=False, diasPendientes=56, excedeLoIndicado=False)),
    dict(nombre="cruza-un-29-de-febrero", du=90, e=30, f="PT24H", retiro="2028-02-01",
         esperado=dict(co=30, agotamiento="2028-03-02", completaLoIndicado=False, diasPendientes=60, excedeLoIndicado=False)),
    dict(nombre="cruza-el-fin-de-ano", du=60, e=30, f="PT24H", retiro="2026-12-20",
         esperado=dict(co=30, agotamiento="2027-01-19", completaLoIndicado=False, diasPendientes=30, excedeLoIndicado=False)),
]


def calcular_cobertura(du: int, e: int, f: str, retiro: str) -> dict:
    co = cobertura_dias(e, f)
    return dict(
        co=co,
        agotamiento=(date.fromisoformat(retiro) + timedelta(days=co)).isoformat(),
        completaLoIndicado=co >= du,
        diasPendientes=max(0, du - co),
        excedeLoIndicado=co > du,
    )


# ---------------------------------------------------------------------------
# Claves de prueba
# ---------------------------------------------------------------------------
#
# SOLO PARA PRUEBAS. Cada clave sale del SHA-256 de una frase pública, así que
# cualquiera puede reconstruirla: no protegen nada y por eso pueden estar en el
# repositorio. Sus identificadores empiezan con «prueba-», y la aplicación de
# producción se niega a cargar un conjunto de claves que contenga alguno.

def _semilla(frase: str) -> bytes:
    return hashlib.sha256(frase.encode()).digest()


def _clave_de_firma(kid: str, **datos) -> tuple[ClaveDeFirma, str]:
    frase = f"MiDosis, clave de firma de prueba {kid}"
    return ClaveDeFirma(kid=kid, privada=_semilla(frase), **datos), frase


def _clave_de_contenido(kid: str) -> tuple[ClaveDeContenido, str]:
    frase = f"MiDosis, clave de contenido de prueba {kid}"
    return ClaveDeContenido(kid=kid, clave=_semilla(frase)), frase


INICIO_2026 = 1767225600   # 01/01/2026 00:00 UTC
SEPTIEMBRE_2026 = 1788220800  # 01/09/2026 00:00 UTC

(FIRMA_1, FRASE_F1) = _clave_de_firma("prueba-1", comuna="13123", vigente_desde=INICIO_2026)
(FIRMA_2, FRASE_F2) = _clave_de_firma("prueba-2", comuna="13123", vigente_desde=SEPTIEMBRE_2026)
(FIRMA_REVOCADA, FRASE_FR) = _clave_de_firma("prueba-revocada", comuna="13123", vigente_desde=INICIO_2026,
                                             revocada=True)
(FIRMA_AJENA, FRASE_FA) = _clave_de_firma("prueba-desconocida", comuna="13123", vigente_desde=INICIO_2026)
(CONTENIDO_1, FRASE_C1) = _clave_de_contenido("prueba-c1")
(CONTENIDO_AJENA, FRASE_CA) = _clave_de_contenido("prueba-c9")

# Lo que la aplicación de prueba trae empaquetado. Las «ajenas» no están.
CONJUNTO_DE_FIRMA = {c.kid: c for c in (FIRMA_1, FIRMA_2, FIRMA_REVOCADA)}
CONJUNTO_DE_CONTENIDO = {CONTENIDO_1.kid: CONTENIDO_1}


def _claves_de_prueba() -> dict:
    firma = []
    for clave, frase in ((FIRMA_1, FRASE_F1), (FIRMA_2, FRASE_F2), (FIRMA_REVOCADA, FRASE_FR),
                         (FIRMA_AJENA, FRASE_FA)):
        firma.append({
            "kid": clave.kid,
            "frase": frase,
            "privada": clave.privada.hex(),
            "publica": clave.publica.hex(),
            "comuna": clave.comuna,
            "vigenteDesde": clave.vigente_desde,
            "vigenteHasta": clave.vigente_hasta,
            "revocada": clave.revocada,
            "enElConjunto": clave.kid in CONJUNTO_DE_FIRMA,
        })
    contenido = [{"kid": c.kid, "frase": f, "clave": c.clave.hex(), "enElConjunto": c.kid in CONJUNTO_DE_CONTENIDO}
                 for c, f in ((CONTENIDO_1, FRASE_C1), (CONTENIDO_AJENA, FRASE_CA))]
    return {
        "advertencia": "SOLO PRUEBAS. Cada clave es el SHA-256 de su frase, que es pública: no protegen nada. "
                       "La aplicación de producción rechaza cualquier clave cuyo kid empiece con «prueba-».",
        "firma": firma,
        "contenido": contenido,
    }


# ---------------------------------------------------------------------------
# Casos de la pila completa
# ---------------------------------------------------------------------------

def _iv(nombre: str) -> bytes:
    """En producción el IV es aleatorio; en los vectores, fijo para que sean reproducibles."""
    return hashlib.sha256(f"iv {nombre}".encode()).digest()[:12]


def _sign1_a_mano(protegida: dict, carga: bytes, firma: bytes) -> bytes:
    return cbor2.dumps(cbor2.CBORTag(18, [cbor2.dumps(protegida), {}, carga, firma]))


def _encrypt0_a_mano(protegida: dict, clave: ClaveDeContenido, iv: bytes, datos: bytes, etiqueta=True) -> bytes:
    protegida_bytes = cbor2.dumps(protegida)
    aad = cbor2.dumps(["Encrypt0", protegida_bytes, b""])
    mensaje = [protegida_bytes, {4: clave.kid.encode(), 5: iv}, AESGCM(clave.clave).encrypt(iv, datos, aad)]
    return cbor2.dumps(cbor2.CBORTag(16, mensaje) if etiqueta else mensaje)


def _invertir_bit(datos: bytes, posicion: int) -> bytes:
    b = bytearray(datos)
    b[posicion] ^= 0x01
    return bytes(b)


def _capas(nombre: str, carga_util, firma=FIRMA_1, contenido=CONTENIDO_1, alterar_firmado=None,
           alterar_cifrado=None, comprimido=None, cifrado=None) -> dict:
    """Arma el código capa por capa. Los ganchos permiten dañar una capa a propósito."""
    carga_bytes = carga_util if isinstance(carga_util, bytes) else cbor(carga_util)
    firmado = pila.firmar(carga_bytes, firma)
    if alterar_firmado:
        firmado = alterar_firmado(firmado)
    if comprimido is None:
        comprimido = pila.comprimir(firmado)
    iv = _iv(nombre)
    if cifrado is None:
        cifrado = pila.cifrar(comprimido, contenido, iv)
    if alterar_cifrado:
        cifrado = alterar_cifrado(cifrado)
    return {
        "codigo": pila.texto(cifrado),
        "capas": {"carga": carga_bytes.hex(), "firmado": firmado.hex(), "comprimido": comprimido.hex(),
                  "iv": iv.hex(), "cifrado": cifrado.hex()},
    }


def _carga_de(nombre: str) -> dict:
    return next(c["carga"] for c in CASOS if c["nombre"] == nombre)


IAT_2 = 1792022400  # 15/10/2026 00:00 UTC, dentro de la vigencia de prueba-2
UNA_HORA = 3600


def _casos_de_codigo() -> list[dict]:
    losartan_ok = carga()
    firmado_ok = pila.firmar(cbor(losartan_ok), FIRMA_1)
    con_otra_carga = cbor(carga(losartan(e=90, co=45)))

    def carga_cambiada(firmado: bytes) -> bytes:
        # La misma firma, pero sobre otra carga: e pasa de 60 a 90.
        valor = cbor2.loads(firmado).value
        return cbor2.dumps(cbor2.CBORTag(18, [valor[0], valor[1], con_otra_carga, valor[3]]))

    def a_otra_version(codigo: dict) -> dict:
        return {**codigo, "codigo": "MD2:" + codigo["codigo"][len(pila.PREFIJO):]}

    def con_caracter_invalido(codigo: dict) -> dict:
        t = codigo["codigo"]
        return {**codigo, "codigo": t[:20] + "a" + t[21:]}

    sin_etiqueta = _encrypt0_a_mano({1: 3}, CONTENIDO_1, _iv("sin-etiqueta"), pila.comprimir(firmado_ok),
                                    etiqueta=False)
    algoritmo_cifrado = _encrypt0_a_mano({1: 1}, CONTENIDO_1, _iv("algoritmo-de-cifrado"), pila.comprimir(firmado_ok))
    bomba = pila.comprimir(bytes(64 * 1024))

    return [
        dict(nombre="codigo-valido-losartan", ahora=IAT + UNA_HORA,
             descripcion="El caso del informe, leído una hora después de emitido.",
             **_capas("valido-losartan", losartan_ok),
             resultado={"valido": True}),
        dict(nombre="codigo-valido-varios-productos", ahora=IAT + UNA_HORA,
             descripcion="Tres productos.",
             **_capas("valido-varios-productos", _carga_de("valido-varios-productos")),
             resultado={"valido": True}),
        dict(nombre="codigo-valido-segunda-clave", ahora=IAT_2 + UNA_HORA,
             descripcion="Firmado con prueba-2, la clave que reemplaza a prueba-1. Las dos conviven (ADR-013).",
             **_capas("valido-segunda-clave", carga(iat=IAT_2, exp=IAT_2 + VIGENCIA_MAXIMA_S,
                                                    jti=ulid(IAT_2, "segunda-clave")), firma=FIRMA_2),
             resultado={"valido": True}),
        dict(nombre="codigo-valido-a-un-segundo-de-expirar", ahora=EXP - 1,
             descripcion="Un segundo antes de «exp» todavía se acepta.",
             **_capas("valido-a-un-segundo", losartan_ok),
             resultado={"valido": True}),
        dict(nombre="codigo-valido-plan-maximo", ahora=IAT + UNA_HORA,
             descripcion="Veinte productos. Demasiado para un QR impreso: es el caso del código corto.",
             **_capas("valido-plan-maximo", _carga_de("limite-plan-maximo")),
             resultado={"valido": True}),

        dict(nombre="codigo-invalido-no-es-de-midosis", ahora=IAT + UNA_HORA,
             descripcion="Un QR cualquiera, como el enlace de un sitio web.",
             codigo="HTTPS://WWW.EJEMPLO.CL/PROMOCION",
             resultado={"valido": False, "motivo": pila.NO_ES_DE_MIDOSIS, "regla": "prefijo"}),
        dict(nombre="codigo-invalido-version-de-pila-futura", ahora=IAT + UNA_HORA,
             descripcion="El prefijo MD2: es de una versión de la pila que esta aplicación no conoce.",
             **a_otra_version(_capas("version-de-pila", losartan_ok)),
             resultado={"valido": False, "motivo": pila.VERSION, "regla": "prefijo"}),
        dict(nombre="codigo-invalido-base45", ahora=IAT + UNA_HORA,
             descripcion="Una letra minúscula: no pertenece al alfabeto Base45.",
             **con_caracter_invalido(_capas("base45", losartan_ok)),
             resultado={"valido": False, "motivo": pila.ALTERADO, "regla": "base45"}),
        dict(nombre="codigo-invalido-truncado", ahora=IAT + UNA_HORA,
             descripcion="Al sobre cifrado le faltan sus dos últimos bytes, como en un código leído a medias.",
             **_capas("truncado", losartan_ok, alterar_cifrado=lambda c: c[:-2]),
             resultado={"valido": False, "motivo": pila.ALTERADO, "regla": "cose"}),
        dict(nombre="codigo-invalido-sin-etiqueta-cose", ahora=IAT + UNA_HORA,
             descripcion="El COSE_Encrypt0 llega sin su etiqueta CBOR 16.",
             **_capas("sin-etiqueta", losartan_ok, cifrado=sin_etiqueta),
             resultado={"valido": False, "motivo": pila.ALTERADO, "regla": "cose"}),
        dict(nombre="codigo-invalido-algoritmo-de-cifrado", ahora=IAT + UNA_HORA,
             descripcion="Cifrado con A128GCM en vez de A256GCM.",
             **_capas("algoritmo-de-cifrado", losartan_ok, cifrado=algoritmo_cifrado),
             resultado={"valido": False, "motivo": pila.ALTERADO, "regla": "algoritmo"}),
        dict(nombre="codigo-invalido-clave-de-contenido-desconocida", ahora=IAT + UNA_HORA,
             descripcion="Cifrado con una clave de contenido que la aplicación no trae: se pide actualizarla.",
             **_capas("contenido-desconocido", losartan_ok, contenido=CONTENIDO_AJENA),
             resultado={"valido": False, "motivo": pila.VERSION, "regla": "clave-de-contenido"}),
        dict(nombre="codigo-invalido-cifrado-alterado", ahora=IAT + UNA_HORA,
             descripcion="Un bit cambiado en el texto cifrado: falla la etiqueta de autenticación de GCM.",
             **_capas("cifrado-alterado", losartan_ok, alterar_cifrado=lambda c: _invertir_bit(c, len(c) - 20)),
             resultado={"valido": False, "motivo": pila.ALTERADO, "regla": "cifrado"}),
        dict(nombre="codigo-invalido-descompresion-excesiva", ahora=IAT + UNA_HORA,
             descripcion="64 KiB de ceros comprimidos en unos cien bytes. Se corta en el límite de 8 KiB.",
             **_capas("descompresion", losartan_ok, comprimido=bomba),
             resultado={"valido": False, "motivo": pila.ALTERADO, "regla": "descompresion"}),
        dict(nombre="codigo-invalido-algoritmo-de-firma", ahora=IAT + UNA_HORA,
             descripcion="La cabecera protegida declara ES256 en vez de EdDSA.",
             **_capas("algoritmo-de-firma", losartan_ok,
                      alterar_firmado=lambda f: _sign1_a_mano({1: -7, 4: b"prueba-1"}, cbor(losartan_ok), bytes(64))),
             resultado={"valido": False, "motivo": pila.ALTERADO, "regla": "algoritmo"}),
        dict(nombre="codigo-invalido-emisor-desconocido", ahora=IAT + UNA_HORA,
             descripcion="Firmado con una clave que no está en el conjunto de la aplicación.",
             **_capas("emisor-desconocido", losartan_ok, firma=FIRMA_AJENA),
             resultado={"valido": False, "motivo": pila.EMISOR_DESCONOCIDO, "regla": "clave-desconocida"}),
        dict(nombre="codigo-invalido-clave-revocada", ahora=IAT + UNA_HORA,
             descripcion="Firmado con una clave que la aplicación conoce, pero marcada como revocada.",
             **_capas("clave-revocada", losartan_ok, firma=FIRMA_REVOCADA),
             resultado={"valido": False, "motivo": pila.EMISOR_DESCONOCIDO, "regla": "clave-revocada"}),
        dict(nombre="codigo-invalido-firma-alterada", ahora=IAT + UNA_HORA,
             descripcion="Un bit cambiado en la firma.",
             **_capas("firma-alterada", losartan_ok, alterar_firmado=lambda f: _invertir_bit(f, len(f) - 1)),
             resultado={"valido": False, "motivo": pila.ALTERADO, "regla": "firma"}),
        dict(nombre="codigo-invalido-carga-alterada", ahora=IAT + UNA_HORA,
             descripcion="Se cambió «e» de 60 a 90 después de firmar, conservando la firma original.",
             **_capas("carga-alterada", losartan_ok, alterar_firmado=carga_cambiada),
             resultado={"valido": False, "motivo": pila.ALTERADO, "regla": "firma"}),
        dict(nombre="codigo-invalido-version-de-carga-futura", ahora=IAT + UNA_HORA,
             descripcion="Firma válida sobre una carga de la versión 2.",
             **_capas("version-de-carga", carga(v=2)),
             resultado={"valido": False, "motivo": VERSION, "regla": "version"}),
        dict(nombre="codigo-invalido-carga-incoherente", ahora=IAT + UNA_HORA,
             descripcion="Firma válida sobre una carga cuya cobertura no cuadra: el error es del emisor.",
             **_capas("carga-incoherente", carga(losartan(co=31))),
             resultado={"valido": False, "motivo": CONTENIDO, "regla": "cobertura"}),
        dict(nombre="codigo-invalido-clave-de-otra-comuna", ahora=IAT + UNA_HORA,
             descripcion="La clave de la comuna 13123 firma un código de la comuna 13101.",
             **_capas("otra-comuna", carga(cm="13101", iss="CL-FP-13101-01")),
             resultado={"valido": False, "motivo": pila.EMISOR_DESCONOCIDO, "regla": "comuna-de-la-clave"}),
        dict(nombre="codigo-invalido-clave-fuera-de-vigencia", ahora=IAT + UNA_HORA,
             descripcion="Firmado con prueba-2, que rige desde el 01/09/2026, un código emitido el 17/08/2026.",
             **_capas("fuera-de-vigencia", losartan_ok, firma=FIRMA_2),
             resultado={"valido": False, "motivo": pila.EMISOR_DESCONOCIDO, "regla": "clave-fuera-de-vigencia"}),
        dict(nombre="codigo-invalido-expirado", ahora=EXP,
             descripcion="Leído justo en «exp»: la vigencia de 72 horas ya terminó (RN-03).",
             **_capas("expirado", losartan_ok),
             resultado={"valido": False, "motivo": pila.EXPIRADO, "regla": "vigencia"}),
    ]


# ---------------------------------------------------------------------------
# Escritura
# ---------------------------------------------------------------------------

def _json(valor) -> str:
    return json.dumps(valor, ensure_ascii=False, indent=2) + "\n"


def construir() -> dict[str, str]:
    """Devuelve el contenido de cada archivo de vectores, por nombre."""
    esquema = pycddl.Schema(ESQUEMA.read_text(encoding="utf-8"))
    archivos: dict[str, str] = {}

    for caso in CASOS:
        datos = caso["bytes_"] if "bytes_" in caso else cbor(caso["carga"])
        try:
            leer(datos, esquema)
            obtenido = {"valido": True}
        except Rechazo as r:
            obtenido = {"valido": False, "motivo": r.motivo, "regla": r.regla}
        if obtenido != caso["resultado"]:
            raise SystemExit(f"{caso['nombre']}: se esperaba {caso['resultado']} y la referencia dio {obtenido}")

        vector = {"descripcion": caso["descripcion"]}
        if "carga" in caso:
            vector["carga"] = caso["carga"]
        vector["cbor"] = datos.hex()
        vector["bytes"] = len(datos)
        vector["resultado"] = caso["resultado"]
        archivos[f"{caso['nombre']}.json"] = _json(vector)

    for caso in COBERTURA:
        obtenido = calcular_cobertura(caso["du"], caso["e"], caso["f"], caso["retiro"])
        if obtenido != caso["esperado"]:
            raise SystemExit(f"cobertura {caso['nombre']}: se esperaba {caso['esperado']} y la referencia dio {obtenido}")
    archivos["cobertura.json"] = _json({
        "descripcion": "Cobertura del tramo entregado (ADR-010): co = piso(e × f / 1 día). "
                       "La fecha de agotamiento es la del retiro más co días.",
        "casos": COBERTURA,
    })

    archivos["claves-de-prueba.json"] = _json(_claves_de_prueba())

    def leer_carga(datos: bytes) -> dict:
        return leer(datos, esquema)

    for caso in _casos_de_codigo():
        try:
            pila.leer_codigo(caso["codigo"], CONJUNTO_DE_FIRMA, CONJUNTO_DE_CONTENIDO, caso["ahora"], leer_carga)
            obtenido = {"valido": True}
        except Rechazo as r:
            obtenido = {"valido": False, "motivo": r.motivo, "regla": r.regla}
        if obtenido != caso["resultado"]:
            raise SystemExit(f"{caso['nombre']}: se esperaba {caso['resultado']} y la referencia dio {obtenido}")
        vector = {"descripcion": caso["descripcion"], "ahora": caso["ahora"], "codigo": caso["codigo"],
                  "caracteres": len(caso["codigo"])}
        if "capas" in caso:
            vector["capas"] = caso["capas"]
        vector["resultado"] = caso["resultado"]
        archivos[f"{caso['nombre']}.json"] = _json(vector)
    return archivos


def main() -> int:
    argumentos = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    argumentos.add_argument("--comprobar", action="store_true", help="no escribe; falla si los vectores no están al día")
    comprobar = argumentos.parse_args().comprobar

    archivos = construir()
    en_disco = {p.name for p in VECTORES.glob("*.json")}

    if comprobar:
        distintos = sorted(n for n, t in archivos.items()
                           if not (VECTORES / n).exists() or (VECTORES / n).read_text(encoding="utf-8") != t)
        sobrantes = sorted(en_disco - archivos.keys())
        for n in distintos:
            print(f"desactualizado: vectores/{n}")
        for n in sobrantes:
            print(f"sobrante: vectores/{n}")
        if distintos or sobrantes:
            print("Corre el generador sin --comprobar y sube el resultado.")
            return 1
        print(f"{len(archivos)} archivos de vectores al día.")
        return 0

    for nombre in en_disco - archivos.keys():
        (VECTORES / nombre).unlink()
    for nombre, texto in archivos.items():
        (VECTORES / nombre).write_text(texto, encoding="utf-8", newline="\n")
    print(f"{len(archivos)} archivos escritos en {VECTORES}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
