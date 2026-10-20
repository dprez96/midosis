# Claves de producción

Las claves con que core emite los códigos (ADR-016). Hasta crearlas, la emisión responde
«no disponible» en producción: core nunca emite con las claves de prueba fuera de los
entornos de prueba.

| Clave | Dónde vive | Para qué |
|---|---|---|
| `firma-<comuna>` | Cloud KMS, llavero `midosis-firma` en `southamerica-west1` | Firmar los códigos de cada comuna (Ed25519). La clave privada nunca sale de KMS. |
| `midosis-clave-contenido` | Secret Manager | Cifrar el sobre (AES-256-GCM). Es nacional. |

## Crear las claves

Lo ejecuta quien administra el proyecto de Google Cloud, después de leer el script:

```bash
PROYECTO=midosis-desarrollo CUENTA_CORE=core@midosis-desarrollo.iam.gserviceaccount.com \
  infra/kms/crear-claves.sh 13123
```

El script crea el llavero si falta, una clave de firma por cada comuna que reciba, el secreto
de la clave de contenido y los permisos de la cuenta de servicio de core: firmar con KMS y
leer el secreto. Nunca imprime la clave de contenido.

## Configurar core

En Cloud Run, con el secreto montado como variable de entorno:

```
MIDOSIS_CLAVES_KMS_LLAVERO=projects/<proyecto>/locations/southamerica-west1/keyRings/midosis-firma
MIDOSIS_CLAVES_CONTENIDO_KID=c1
MIDOSIS_CLAVES_CONTENIDO_VALOR=<secreto midosis-clave-contenido, versión 1>
```

## Empaquetar las claves en la aplicación

La aplicación verifica sin conexión con las claves públicas empaquetadas (ADR-013), y
descifra con la clave de contenido (ADR-003). **El repositorio es público**: la clave de
contenido de producción no puede quedar en `src/main/assets`. Se genera al compilar la
versión de producción, en un archivo que git ignora:

```bash
PROYECTO=midosis-desarrollo infra/kms/exportar-claves.sh 13123 \
  > paciente/app/src/release/assets/claves-de-confianza.json
```

La compilación de depuración sigue usando las claves de prueba de `src/debug/assets`.

## Rotar una clave

Crear una versión nueva de `firma-<comuna>` en KMS. Core empieza a firmar con ella en menos
de diez minutos. Antes, publicar una versión de la aplicación que traiga las dos claves
públicas, la vieja y la nueva: los teléfonos que no se actualicen no reconocerán los códigos
nuevos (riesgo declarado en ADR-013).
