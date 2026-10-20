#!/usr/bin/env bash
# Escribe en la salida estándar el claves-de-confianza.json de producción de la aplicación:
# las claves públicas de firma de las comunas indicadas y la clave de contenido. Ver
# infra/kms/README.md: el resultado va a un archivo que git ignora, nunca al repositorio.
#
#   PROYECTO=<proyecto> infra/kms/exportar-claves.sh <comuna>... > paciente/app/src/release/assets/claves-de-confianza.json
set -euo pipefail

: "${PROYECTO:?indica el proyecto de Google Cloud en PROYECTO}"
[ "$#" -ge 1 ] || { echo "uso: $0 <comuna> [<comuna>...]" >&2; exit 1; }

REGION=southamerica-west1
LLAVERO=midosis-firma
SECRETO=midosis-clave-contenido

emisores=()
for comuna in "$@"; do
  [[ "$comuna" =~ ^[0-9]{5}$ ]] || { echo "comuna inválida: $comuna" >&2; exit 1; }
  # Cada versión habilitada: la app conserva las anteriores para leer códigos ya emitidos.
  while IFS=$'\t' read -r nombre creada; do
    version="${nombre##*/}"
    pem=$(gcloud kms keys versions get-public-key "$version" --key "firma-$comuna" --keyring "$LLAVERO" \
      --location "$REGION" --project "$PROYECTO")
    # Una clave pública Ed25519 en DER son 12 bytes de cabecera y los 32 de la clave.
    publica=$(echo "$pem" | openssl pkey -pubin -outform DER | tail -c 32 | od -An -tx1 | tr -d ' \n')
    desde=$(date -d "$creada" +%s)
    emisores+=("{\"kid\": \"$comuna-$version\", \"publica\": \"$publica\", \"comuna\": \"$comuna\", \"vigenteDesde\": $desde, \"vigenteHasta\": null, \"revocada\": false}")
  done < <(gcloud kms keys versions list --key "firma-$comuna" --keyring "$LLAVERO" --location "$REGION" \
    --project "$PROYECTO" --filter "state=ENABLED" --format "value[separator=\"\t\"](name,createTime)")
done

contenido=$(gcloud secrets versions access 1 --secret "$SECRETO" --project "$PROYECTO" | base64 -d | od -An -tx1 | tr -d ' \n')

printf '{\n  "descripcion": "Claves de producción. Generado por infra/kms/exportar-claves.sh: no se versiona.",\n'
printf '  "emisores": [\n'
for i in "${!emisores[@]}"; do
  separador=","
  [ "$i" -eq $((${#emisores[@]} - 1)) ] && separador=""
  printf '    %s%s\n' "${emisores[$i]}" "$separador"
done
printf '  ],\n'
printf '  "contenido": [{"kid": "c1", "clave": "%s"}]\n}\n' "$contenido"
