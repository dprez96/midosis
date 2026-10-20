#!/usr/bin/env bash
# Crea las claves de producción de MiDosis (ADR-016). Ver infra/kms/README.md.
#
#   PROYECTO=<proyecto> CUENTA_CORE=<cuenta de servicio de core> infra/kms/crear-claves.sh <comuna>...
#
# Es idempotente: lo que ya existe no se toca. Nunca imprime la clave de contenido.
set -euo pipefail

: "${PROYECTO:?indica el proyecto de Google Cloud en PROYECTO}"
: "${CUENTA_CORE:?indica la cuenta de servicio de core en CUENTA_CORE}"
[ "$#" -ge 1 ] || { echo "uso: $0 <comuna> [<comuna>...]" >&2; exit 1; }

REGION=southamerica-west1
LLAVERO=midosis-firma
SECRETO=midosis-clave-contenido

if ! gcloud kms keyrings describe "$LLAVERO" --location "$REGION" --project "$PROYECTO" >/dev/null 2>&1; then
  gcloud kms keyrings create "$LLAVERO" --location "$REGION" --project "$PROYECTO"
fi

for comuna in "$@"; do
  [[ "$comuna" =~ ^[0-9]{5}$ ]] || { echo "comuna inválida: $comuna" >&2; exit 1; }
  CLAVE="firma-$comuna"
  if ! gcloud kms keys describe "$CLAVE" --keyring "$LLAVERO" --location "$REGION" --project "$PROYECTO" >/dev/null 2>&1; then
    gcloud kms keys create "$CLAVE" --keyring "$LLAVERO" --location "$REGION" --project "$PROYECTO" \
      --purpose asymmetric-signing --default-algorithm ec-sign-ed25519 --protection-level software
  fi
  # Core firma; no puede leer ni exportar la clave privada.
  gcloud kms keys add-iam-policy-binding "$CLAVE" --keyring "$LLAVERO" --location "$REGION" --project "$PROYECTO" \
    --member "serviceAccount:$CUENTA_CORE" --role roles/cloudkms.signerVerifier >/dev/null
  # Core lista las versiones para firmar con la vigente.
  gcloud kms keys add-iam-policy-binding "$CLAVE" --keyring "$LLAVERO" --location "$REGION" --project "$PROYECTO" \
    --member "serviceAccount:$CUENTA_CORE" --role roles/cloudkms.viewer >/dev/null
  echo "clave de firma lista: $CLAVE"
done

if ! gcloud secrets describe "$SECRETO" --project "$PROYECTO" >/dev/null 2>&1; then
  # 32 bytes al azar, en Base64, directo al secreto: nunca pasan por la pantalla ni por un archivo.
  openssl rand -base64 32 | tr -d '\n' | gcloud secrets create "$SECRETO" --project "$PROYECTO" \
    --replication-policy user-managed --locations "$REGION" --data-file=-
fi
gcloud secrets add-iam-policy-binding "$SECRETO" --project "$PROYECTO" \
  --member "serviceAccount:$CUENTA_CORE" --role roles/secretmanager.secretAccessor >/dev/null
echo "clave de contenido lista: $SECRETO"
