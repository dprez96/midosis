"""
Prepara una demostración de punta a punta en el equipo, con datos sintéticos y las claves de
prueba de contrato/vectores (que no protegen nada y la app de producción rechaza).

Deja dos tratamientos recién emitidos, vigentes por 72 horas:

  1. Un QR en infra/local/demo/salida/qr-demo.png, para escanearlo con la cámara (HU-11).
  2. Un código corto y su PIN registrados en la base local, para cargarlos a mano (HU-10).

Requiere la base de infra/local/compose.yaml levantada y core corriendo con el perfil local
(que aplica las migraciones). En el teléfono, con la app de depuración instalada:

    adb reverse tcp:8080 tcp:8080

Uso, desde la raíz del repositorio y con el entorno del generador de vectores:

    python infra/local/demo/preparar_demo.py
"""

from __future__ import annotations

import hashlib
import secrets
import subprocess
import sys
import time
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(RAIZ / "contrato" / "herramientas"))

import segno  # noqa: E402

import pila  # noqa: E402
from generar_vectores import CONTENIDO_1, FIRMA_1, cbor, gtin, ulid  # noqa: E402

SALIDA = Path(__file__).resolve().parent / "salida"
CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
VIGENCIA_S = 72 * 3600


def producto(cuerpo: str, etiqueta: str, activo: str, f: str, du: int, e: int, o: list[int] | None = None) -> dict:
    minutos = int(f[2:-1]) * 60
    p = {"c": gtin(cuerpo), "n": etiqueta, "a": activo, "d": "1 comprimido", "f": f,
         "du": du, "e": e, "co": e * minutos // 1440, "r": "VO"}
    if o:
        p["o"] = o
    return p


def codigo(productos: list[dict], semilla: str) -> tuple[str, dict]:
    ahora = int(time.time())
    carga = {"v": 1, "cm": "13123", "iss": "CL-FP-13123-01", "jti": ulid(ahora, semilla + str(ahora)),
             "iat": ahora, "exp": ahora + VIGENCIA_S, "rx": productos}
    firmado = pila.firmar(cbor(carga), FIRMA_1)
    texto = pila.texto(pila.cifrar(pila.comprimir(firmado), CONTENIDO_1, secrets.token_bytes(12)))
    return texto, carga


def codigo_corto() -> str:
    """Nueve caracteres al azar y el verificador Luhn mod 32, como CodigoCorto del contrato."""
    cuerpo = "".join(secrets.choice(CROCKFORD) for _ in range(9))
    suma, factor = 0, 2
    for c in reversed(cuerpo):
        sumando = factor * CROCKFORD.index(c)
        suma += sumando // 32 + sumando % 32
        factor = 1 if factor == 2 else 2
    return cuerpo + CROCKFORD[(32 - suma % 32) % 32]


def registrar_sobre(corto: str, texto: str) -> str:
    """Inserta el sobre en la base local con el PIN derivado como lo hace core (PBKDF2, 100 000)."""
    pin = f"{secrets.randbelow(10_000):04d}"
    sal = secrets.token_bytes(16)
    derivado = hashlib.pbkdf2_hmac("sha256", pin.encode(), sal, 100_000, 32)
    sql = (
        "INSERT INTO sobre (codigo_corto, comuna, codigo, pin_derivado, pin_sal, expira_en) VALUES "
        f"('{corto}', '13123', '{texto}', decode('{derivado.hex()}', 'hex'), decode('{sal.hex()}', 'hex'), "
        f"now() + interval '72 hours');"
    )
    # El superusuario de la base local, solo para sembrar la demostración.
    subprocess.run(["docker", "exec", "-i", "midosis-local-postgres-1", "psql", "-q", "-U", "postgres", "-d", "midosis",
                    "-v", "ON_ERROR_STOP=1", "-c", sql], check=True)
    return pin


def main() -> None:
    SALIDA.mkdir(exist_ok=True)

    qr, carga_qr = codigo([
        producto("780225001234", "Losartán 50 mg comp.", "Losartán", "PT12H", 180, 60, [2]),
        producto("780225001240", "Metformina 850 mg", "Metformina", "PT12H", 90, 60, [2]),
    ], "demo-qr")
    segno.make(qr, error="m", micro=False).save(SALIDA / "qr-demo.png", scale=8, border=4)

    texto_corto, _ = codigo([producto("780225001250", "Atorvastatina 20 mg", "Atorvastatina", "PT24H", 90, 30, [3])],
                            "demo-corto")
    corto = codigo_corto()
    pin = registrar_sobre(corto, texto_corto)

    print(f"QR del tratamiento (losartán y metformina): {SALIDA / 'qr-demo.png'}")
    print(f"  {len(qr)} caracteres, vigente hasta {time.strftime('%d-%m-%Y %H:%M', time.localtime(carga_qr['exp']))}")
    print(f"Código corto (atorvastatina): {corto[:5]}-{corto[5:]}   PIN: {pin}")


if __name__ == "__main__":
    main()
