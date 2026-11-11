#!/usr/bin/env bash
# Compila core, arma su imagen con Cloud Build y la publica en Cloud Run. Ver infra/cloudrun/README.md.
#
#   PROYECTO=midosis-desarrollo infra/cloudrun/desplegar.sh
#
# Antes: infra/kms/crear-claves.sh y infra/cloudrun/preparar-base.sh.
set -euo pipefail

: "${PROYECTO:?indica el proyecto de Google Cloud en PROYECTO}"
REGION=southamerica-west1
INSTANCIA=midosis-db
SERVICIO=midosis-core
REPOSITORIO=midosis
CUENTA_CORE="midosis-core@${PROYECTO}.iam.gserviceaccount.com"
RAIZ="$(cd "$(dirname "$0")/../.." && pwd)"
VERSION="$(git -C "$RAIZ" rev-parse --short HEAD)"
IMAGEN="${REGION}-docker.pkg.dev/${PROYECTO}/${REPOSITORIO}/core:${VERSION}"

# 1. El jar ejecutable de core (sin correr las pruebas: las corre la integración continua).
(cd "$RAIZ/core" && ./gradlew bootJar --no-daemon -q)
JAR="$(ls "$RAIZ"/core/build/libs/core-*.jar | grep -v plain | head -1)"

# 2. La imagen, construida en Cloud Build a partir del jar y del Dockerfile.
if ! gcloud artifacts repositories describe "$REPOSITORIO" --location "$REGION" --project "$PROYECTO" >/dev/null 2>&1; then
  gcloud artifacts repositories create "$REPOSITORIO" --repository-format docker --location "$REGION" --project "$PROYECTO"
fi
CONTEXTO="$(mktemp -d)"
trap 'rm -rf "$CONTEXTO"' EXIT
cp "$RAIZ/infra/cloudrun/Dockerfile" "$CONTEXTO/Dockerfile"
cp "$JAR" "$CONTEXTO/app.jar"
gcloud builds submit "$CONTEXTO" --tag "$IMAGEN" --project "$PROYECTO" --region "$REGION"

# 3. El servicio. La base se alcanza con el conector de Cloud SQL y la identidad de la cuenta
# de servicio; las contraseñas y la clave de contenido llegan desde Secret Manager.
URL_BASE="jdbc:postgresql:///midosis?cloudSqlInstance=${PROYECTO}:${REGION}:${INSTANCIA}&socketFactory=com.google.cloud.sql.postgres.SocketFactory"
gcloud run deploy "$SERVICIO" --project "$PROYECTO" --region "$REGION" --image "$IMAGEN" \
  --service-account "$CUENTA_CORE" \
  --add-cloudsql-instances "${PROYECTO}:${REGION}:${INSTANCIA}" \
  --set-env-vars "^|^MIDOSIS_DB_URL=${URL_BASE}|MIDOSIS_DB_USUARIO=midosis_app|MIDOSIS_DB_DUENO=midosis_dueno|MIDOSIS_CLAVES_KMS_LLAVERO=projects/${PROYECTO}/locations/${REGION}/keyRings/midosis-firma|MIDOSIS_CLAVES_CONTENIDO_KID=c1" \
  --set-secrets "MIDOSIS_DB_CLAVE=midosis-db-app-clave:latest,MIDOSIS_DB_DUENO_CLAVE=midosis-db-dueno-clave:latest,MIDOSIS_CLAVES_CONTENIDO_VALOR=midosis-clave-contenido:1" \
  --cpu 1 --memory 1Gi --min-instances 0 --max-instances 2 \
  --allow-unauthenticated

# La API exige token en todo salvo el canje y la consulta de estado: el acceso público al
# servicio es necesario para la web y la aplicación, no abre ninguna ruta protegida.
echo "core publicado: $(gcloud run services describe "$SERVICIO" --project "$PROYECTO" --region "$REGION" --format='value(status.url)')"
