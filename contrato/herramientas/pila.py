"""
Referencia de la pila de codificación del código de tratamiento (ADR-004).

    carga (CBOR) -> COSE_Sign1 Ed25519 -> zlib -> COSE_Encrypt0 A256GCM -> Base45 -> «MD1:»

La emisión usa pycose, y la lectura sigue paso a paso el orden de la sección
7.6.1 del informe. Las reglas y los motivos de rechazo están en
contrato/esquema/pila-v1.md.
"""

from __future__ import annotations

import io
import re
import zlib
from collections.abc import Mapping
from dataclasses import dataclass

import cbor2
from cryptography.exceptions import InvalidSignature, InvalidTag
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey, Ed25519PublicKey
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from pycose.algorithms import A256GCM, EdDSA
from pycose.headers import IV, KID, Algorithm
from pycose.keys import OKPKey, SymmetricKey
from pycose.keys.curves import Ed25519
from pycose.messages import Enc0Message, Sign1Message

PREFIJO = "MD1:"
LIMITE_DESCOMPRIMIDO = 8 * 1024
ALG_EDDSA, ALG_A256GCM = -8, 3
ETIQUETA_SIGN1, ETIQUETA_ENCRYPT0 = 18, 16
H_ALG, H_KID, H_IV = 1, 4, 5

# Motivos de la pila. Los de la carga útil (estructura-invalida,
# version-no-soportada, contenido-incoherente) se suman a estos.
NO_ES_DE_MIDOSIS = "no-es-de-midosis"
VERSION = "version-no-soportada"
ALTERADO = "alterado"
EMISOR_DESCONOCIDO = "emisor-desconocido"
EXPIRADO = "expirado"


class Rechazo(Exception):
    def __init__(self, motivo: str, regla: str):
        super().__init__(f"{motivo}/{regla}")
        self.motivo, self.regla = motivo, regla


# ---------------------------------------------------------------------------
# Base45 (RFC 9285)
# ---------------------------------------------------------------------------

ALFABETO_BASE45 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:"


def base45(datos: bytes) -> str:
    salida = []
    for i in range(0, len(datos), 2):
        if i + 1 < len(datos):
            n = datos[i] * 256 + datos[i + 1]
            c, n = n % 45, n // 45
            d, e = n % 45, n // 45
            salida += [ALFABETO_BASE45[c], ALFABETO_BASE45[d], ALFABETO_BASE45[e]]
        else:
            n = datos[i]
            salida += [ALFABETO_BASE45[n % 45], ALFABETO_BASE45[n // 45]]
    return "".join(salida)


def desde_base45(texto: str) -> bytes:
    """Estricta: rechaza caracteres fuera del alfabeto, largos imposibles y valores que no caben."""
    if len(texto) % 3 == 1:
        raise ValueError("largo imposible")
    try:
        valores = [ALFABETO_BASE45.index(c) for c in texto]
    except ValueError:
        raise ValueError("carácter fuera del alfabeto")
    salida = bytearray()
    for i in range(0, len(valores), 3):
        grupo = valores[i:i + 3]
        n = sum(v * 45 ** k for k, v in enumerate(grupo))
        if len(grupo) == 3:
            if n > 0xFFFF:
                raise ValueError("triple fuera de rango")
            salida += bytes([n >> 8, n & 0xFF])
        else:
            if n > 0xFF:
                raise ValueError("par fuera de rango")
            salida.append(n)
    return bytes(salida)


# ---------------------------------------------------------------------------
# Claves
# ---------------------------------------------------------------------------

@dataclass(frozen=True)
class ClaveDeFirma:
    kid: str
    privada: bytes
    comuna: str
    vigente_desde: int
    vigente_hasta: int | None = None
    revocada: bool = False

    @property
    def publica(self) -> bytes:
        return (Ed25519PrivateKey.from_private_bytes(self.privada).public_key()
                .public_bytes(serialization.Encoding.Raw, serialization.PublicFormat.Raw))


@dataclass(frozen=True)
class ClaveDeContenido:
    kid: str
    clave: bytes


# ---------------------------------------------------------------------------
# Emisión
# ---------------------------------------------------------------------------

def firmar(carga: bytes, clave: ClaveDeFirma) -> bytes:
    mensaje = Sign1Message(phdr={Algorithm: EdDSA, KID: clave.kid.encode()}, payload=carga)
    mensaje.key = OKPKey(crv=Ed25519, d=clave.privada, x=clave.publica)
    return mensaje.encode(tag=True)


def comprimir(datos: bytes) -> bytes:
    return zlib.compress(datos, 9)


def cifrar(datos: bytes, clave: ClaveDeContenido, iv: bytes) -> bytes:
    mensaje = Enc0Message(phdr={Algorithm: A256GCM}, uhdr={KID: clave.kid.encode(), IV: iv}, payload=datos)
    mensaje.key = SymmetricKey(k=clave.clave)
    return mensaje.encode(tag=True)


def texto(cifrado: bytes) -> str:
    return PREFIJO + base45(cifrado)


# ---------------------------------------------------------------------------
# Lectura, en el orden de la sección 7.6.1
# ---------------------------------------------------------------------------

def _un_solo_elemento(datos: bytes):
    flujo = io.BytesIO(datos)
    valor = cbor2.CBORDecoder(flujo).decode()
    if flujo.tell() != len(datos):
        raise ValueError("sobran bytes")
    return valor


def _mensaje_cose(datos: bytes, etiqueta: int, largo: int) -> list:
    try:
        valor = _un_solo_elemento(datos)
    except Exception:
        raise Rechazo(ALTERADO, "cose")
    if not (isinstance(valor, cbor2.CBORTag) and valor.tag == etiqueta
            and isinstance(valor.value, (list, tuple)) and len(valor.value) == largo):
        raise Rechazo(ALTERADO, "cose")
    partes = list(valor.value)
    if not isinstance(partes[0], bytes) or not isinstance(partes[1], Mapping):
        raise Rechazo(ALTERADO, "cose")
    try:
        protegida = _un_solo_elemento(partes[0]) if partes[0] else {}
    except Exception:
        raise Rechazo(ALTERADO, "cose")
    if not isinstance(protegida, Mapping):
        raise Rechazo(ALTERADO, "cose")
    return [protegida] + partes


def descifrar_capa(datos: bytes, contenido: dict[str, ClaveDeContenido]) -> bytes:
    protegida, protegida_bytes, libre, cifrado = _mensaje_cose(datos, ETIQUETA_ENCRYPT0, 3)
    if protegida != {H_ALG: ALG_A256GCM}:
        raise Rechazo(ALTERADO, "algoritmo")
    kid, iv = libre.get(H_KID), libre.get(H_IV)
    if set(libre) != {H_KID, H_IV} or not isinstance(kid, bytes) or not isinstance(iv, bytes) \
            or len(iv) != 12 or not isinstance(cifrado, bytes):
        raise Rechazo(ALTERADO, "cose")
    clave = contenido.get(kid.decode("utf-8", "replace"))
    if clave is None:
        # Una clave de contenido que la aplicación no conoce sale de una versión
        # más nueva del sistema: se pide actualizar.
        raise Rechazo(VERSION, "clave-de-contenido")
    aad = cbor2.dumps(["Encrypt0", protegida_bytes, b""])
    try:
        return AESGCM(clave.clave).decrypt(iv, cifrado, aad)
    except InvalidTag:
        raise Rechazo(ALTERADO, "cifrado")


def descomprimir(datos: bytes) -> bytes:
    try:
        d = zlib.decompressobj()
        salida = d.decompress(datos, LIMITE_DESCOMPRIMIDO + 1)
    except zlib.error:
        raise Rechazo(ALTERADO, "descompresion")
    if len(salida) > LIMITE_DESCOMPRIMIDO or d.unconsumed_tail or not d.eof or d.unused_data:
        raise Rechazo(ALTERADO, "descompresion")
    return salida


def verificar_firma(datos: bytes, firmas: dict[str, ClaveDeFirma]) -> tuple[bytes, ClaveDeFirma]:
    protegida, protegida_bytes, _libre, carga, firma = _mensaje_cose(datos, ETIQUETA_SIGN1, 4)
    if protegida.get(H_ALG) != ALG_EDDSA:
        raise Rechazo(ALTERADO, "algoritmo")
    kid = protegida.get(H_KID)
    if set(protegida) != {H_ALG, H_KID} or not isinstance(kid, bytes) \
            or not isinstance(carga, bytes) or not isinstance(firma, bytes) or len(firma) != 64:
        raise Rechazo(ALTERADO, "cose")
    clave = firmas.get(kid.decode("utf-8", "replace"))
    if clave is None:
        raise Rechazo(EMISOR_DESCONOCIDO, "clave-desconocida")
    if clave.revocada:
        raise Rechazo(EMISOR_DESCONOCIDO, "clave-revocada")
    estructura = cbor2.dumps(["Signature1", protegida_bytes, b"", carga])
    try:
        Ed25519PublicKey.from_public_bytes(clave.publica).verify(firma, estructura)
    except InvalidSignature:
        raise Rechazo(ALTERADO, "firma")
    return carga, clave


PATRON_PREFIJO = re.compile(r"^MD([0-9]+):")


def leer_codigo(codigo: str, firmas: dict[str, ClaveDeFirma], contenido: dict[str, ClaveDeContenido],
                ahora: int, leer_carga) -> dict:
    """Devuelve la carga leída o lanza un Rechazo. leer_carga es la lectura de la carga útil."""
    prefijo = PATRON_PREFIJO.match(codigo)
    if prefijo is None:
        raise Rechazo(NO_ES_DE_MIDOSIS, "prefijo")
    if prefijo.group(0) != PREFIJO:
        raise Rechazo(VERSION, "prefijo")
    try:
        cifrado = desde_base45(codigo[len(PREFIJO):])
    except ValueError:
        raise Rechazo(ALTERADO, "base45")

    firmado = descomprimir(descifrar_capa(cifrado, contenido))
    carga_bytes, clave = verificar_firma(firmado, firmas)
    carga = leer_carga(carga_bytes)

    if carga["cm"] != clave.comuna:
        raise Rechazo(EMISOR_DESCONOCIDO, "comuna-de-la-clave")
    if carga["iat"] < clave.vigente_desde or (clave.vigente_hasta is not None and carga["iat"] >= clave.vigente_hasta):
        raise Rechazo(EMISOR_DESCONOCIDO, "clave-fuera-de-vigencia")
    if ahora >= carga["exp"]:
        raise Rechazo(EXPIRADO, "vigencia")
    return carga
