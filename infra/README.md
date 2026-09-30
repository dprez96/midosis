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

## Firebase Authentication

Proyecto de desarrollo: **MiDosis Desarrollo** (`midosis-desarrollo`), plan Spark, sin costo.
Gemini y Google Analytics desactivados.

- **Quién inicia sesión:** el personal de la farmacia, con correo y contraseña. Los pacientes
  no inician sesión (HU-11).
- **Perfil de cada usuario:** comuna y rol, como claims del token. La consola de Firebase no
  permite editarlos; se asignan con la herramienta de esta carpeta.

```bash
cd infra/firebase
npm install
node asignar-perfil.mjs qf@midosis.cl                  # ver el perfil
node asignar-perfil.mjs qf@midosis.cl 13123 qf         # asignarlo
```

Roles: `auxiliar`, `qf` (químico farmacéutico) y `administrador` (comunal). El cambio vale
desde el próximo inicio de sesión.

La herramienta usa las credenciales de aplicación de quien la ejecuta, **sin archivos de
clave**:

```bash
gcloud auth application-default login
gcloud auth application-default set-quota-project midosis-desarrollo
```

No se descargan claves de cuenta de servicio: son secretos permanentes y, si se filtran, hay
que revocarlos.

