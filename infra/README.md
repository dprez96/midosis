# Infraestructura

Google Cloud, región `southamerica-west1` (Santiago). Los servicios centrales
residen en Chile.

| Servicio | Uso |
|---|---|
| Cloud Run | Servicios de `core/`, facturación por solicitud y escalado a cero |
| Cloud SQL | PostgreSQL con cifrado en reposo y seguridad a nivel de fila |
| Cloud KMS | Custodia de las claves de firma y cifrado |
| Secret Manager | Credenciales de la aplicación |
| Firebase Hosting | Entrega de `farmacia/` |

Sin Kubernetes, sin clúster permanente y sin Redis administrado durante el piloto:
la caché solo se incorpora si las métricas superan los umbrales definidos.

## Ambientes

Desarrollo y QA: datos sintéticos, siempre. Producción: datos reales con controles
completos. Un volcado de producción no se restaura en QA bajo ninguna circunstancia.
