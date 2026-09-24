# Seguridad

## Secretos

Ninguna credencial, clave privada, token ni cadena de conexión se versiona. El
repositorio tiene activado el escaneo de secretos; si alguno se filtra, hay que
**rotarlo**, no solo borrar el commit: el valor ya quedó expuesto en el historial.

Las claves de firma y cifrado viven en Cloud KMS y Secret Manager. Nunca en disco,
nunca en variables de entorno de desarrollo compartidas.

## Claves de firma

Las claves privadas de firma Ed25519 no salen del almacén gestionado. El desarrollo
usa un par de claves de prueba, generado localmente y declarado como tal, que jamás
se usa para emitir un código destinado a un paciente.

## Reporte de vulnerabilidades

Los hallazgos de seguridad se abren como *issue* privado de seguridad, no como
*issue* público del tablero, y se tratan antes que cualquier historia en curso.

## Modelo de amenazas

El modelo STRIDE del código de tratamiento está en `docs/modelo-de-amenazas.md`
y se revisa ante cada cambio arquitectónico, no solo al inicio.
