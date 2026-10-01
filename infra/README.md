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

## Correr en el equipo

`local/` levanta un PostgreSQL con los mismos usuarios que producción: `midosis_dueno`,
dueño de las tablas, y `midosis_app`, sin privilegios de dueño. Ninguno es superusuario,
así que el aislamiento entre comunas rige igual que en la nube. Solo escucha en el propio
equipo.

Con Docker Desktop abierto, desde la raíz del repositorio:

```bash
docker compose -f infra/local/compose.yaml up -d
cd core && ./gradlew bootRun --args='--spring.profiles.active=local'
cd farmacia && npm run dev        # en otra terminal; ver farmacia/README.md
```

El perfil `local` de `core` carga `local/semilla`: diez productos sintéticos en la comuna
13123, la de los usuarios de prueba. Para partir de cero:
`docker compose -f infra/local/compose.yaml down -v`.

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

