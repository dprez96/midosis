"""
Genera los vectores de prueba de la carga útil y de la cobertura del tramo.

Es una implementación de referencia, escrita aparte de la de Kotlin a propósito:
si core, paciente y el motor se probaran solo contra vectores producidos por su
propio código, un error compartido pasaría inadvertido.

Cada caso declara a mano el resultado que espera. El generador lo comprueba
contra el esquema (carga-v1.cddl) y contra las reglas de carga-v1.md antes de
escribir nada: si no coinciden, se detiene.

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

class Rechazo(Exception):
    def __init__(self, motivo: str, regla: str):
        super().__init__(f"{motivo}/{regla}")
        self.motivo, self.regla = motivo, regla


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
