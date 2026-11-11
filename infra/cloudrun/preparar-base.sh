#!/usr/bin/env bash
# Prepara la base de core en Cloud SQL: contraseñas en Secret Manager, base «midosis» y los
# usuarios midosis_dueno y midosis_app sin privilegios de superusuario. Ver infra/cloudrun/README.md.
#
#   PROYECTO=midosis-desarrollo infra/cloudrun/preparar-base.sh
#
# Requiere gcloud con sesión iniciada y Docker abierto (corre psql en un contenedor).
# Es idempotente. Nunca imprime una contraseña: van de Secret Manager a variables de entorno.
set -euo pipefail

: "${PROYECTO:?indica el proyecto de Google Cloud en PROYECTO}"
REGION=southamerica-west1
INSTANCIA=midosis-db
BASE=midosis
CUENTA_CORE="midosis-core@${PROYECTO}.iam.gserviceaccount.com"
AQUI="$(cd "$(dirname "$0")" && pwd)"

secreto() {
  local nombre="$1"
  if ! gcloud secrets describe "$nombre" --project "$PROYECTO" >/dev/null 2>&1; then
    # Sin / + = para que la contraseña no necesite escaparse en una URL o en la consola.
    openssl rand -base64 30 | tr -d '\n/+=' | gcloud secrets create "$nombre" --project "$PROYECTO" \
      --replication-policy user-managed --locations "$REGION" --data-file=- >/dev/null
    echo "secreto creado: $nombre"
  fi
}
leer() { gcloud secrets versions access latest --secret "$1" --project "$PROYECTO"; }

secreto midosis-db-postgres-clave
secreto midosis-db-dueno-clave
secreto midosis-db-app-clave
# Core lee las contraseñas del dueño (migraciones) y de la aplicación. La de postgres, no.
for s in midosis-db-dueno-clave midosis-db-app-clave; do
  gcloud secrets add-iam-policy-binding "$s" --project "$PROYECTO" \
    --member "serviceAccount:$CUENTA_CORE" --role roles/secretmanager.secretAccessor >/dev/null
done

echo "esperando que la instancia $INSTANCIA esté lista..."
until [ "$(gcloud sql instances describe "$INSTANCIA" --project "$PROYECTO" --format='value(state)')" = "RUNNABLE" ]; do
  sleep 20
done

gcloud sql users set-password postgres --instance "$INSTANCIA" --project "$PROYECTO" \
  --password "$(leer midosis-db-postgres-clave)" >/dev/null
if ! gcloud sql databases describe "$BASE" --instance "$INSTANCIA" --project "$PROYECTO" >/dev/null 2>&1; then
  gcloud sql databases create "$BASE" --instance "$INSTANCIA" --project "$PROYECTO" >/dev/null
fi

# psql entra por la IP pública, autorizada solo para este equipo mientras dura el script.
IP_PUBLICA="$(curl -s https://api.ipify.org)"
IP_BASE="$(gcloud sql instances describe "$INSTANCIA" --project "$PROYECTO" --format='value(ipAddresses[0].ipAddress)')"
cerrar() {
  gcloud sql instances patch "$INSTANCIA" --project "$PROYECTO" --clear-authorized-networks --quiet >/dev/null
  echo "acceso temporal de este equipo retirado"
}
trap cerrar EXIT
gcloud sql instances patch "$INSTANCIA" --project "$PROYECTO" --authorized-networks "${IP_PUBLICA}/32" --quiet >/dev/null

PGPASSWORD="$(leer midosis-db-postgres-clave)" \
CLAVE_DUENO="$(leer midosis-db-dueno-clave)" \
CLAVE_APP="$(leer midosis-db-app-clave)" \
  docker run --rm -i -e PGPASSWORD -e CLAVE_DUENO -e CLAVE_APP postgres:16-alpine \
    psql "host=$IP_BASE port=5432 dbname=$BASE user=postgres sslmode=require" < "$AQUI/roles.sql"

echo "base lista: midosis_dueno y midosis_app sin superusuario ni BYPASSRLS"
