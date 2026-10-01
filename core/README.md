# MiDosis Core

Servicios centrales: emisión, canje, revocación y consulta de estado del código de
tratamiento; catálogo de productos; motor de reglas; auditoría y privacidad.

Kotlin sobre Spring Boot, arquitectura hexagonal. La lógica de dominio (posología,
cobertura del tramo, validación de coherencia) no conoce el framework ni la base de
datos: se prueba sin levantar el contexto de Spring.

## Pruebas

```bash
./gradlew test
```

Requiere Docker Desktop abierto: las pruebas levantan un PostgreSQL real con
Testcontainers. No hace falta instalar ni configurar una base de datos.

## Correr en el equipo

Con la base local de `infra/local` levantada (ver `infra/README.md`):

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

El perfil `local` usa esa base y carga el catálogo sintético de `infra/local/semilla`.
Los tokens se validan contra Firebase igual que en cualquier ambiente.

## API

| Método y ruta | Qué hace |
|---|---|
| `GET /api/catalogo/productos` | Catálogo de la comuna del token |
| `GET /api/catalogo/productos/{gtin}` | Un producto; 404 si no está en la comuna, exista o no en otra |
| `GET /api/catalogo/productos?texto=` | Búsqueda manual por nombre, principio activo o comienzo del código, sin distinguir tildes ni mayúsculas (HU-02) |

## Configuración

| Variable | Qué es |
|---|---|
| `MIDOSIS_DB_URL` | URL JDBC de PostgreSQL |
| `MIDOSIS_DB_USUARIO`, `MIDOSIS_DB_CLAVE` | Usuario de la aplicación, sin privilegios de dueño |
| `MIDOSIS_DB_DUENO`, `MIDOSIS_DB_DUENO_CLAVE` | Usuario dueño, solo para las migraciones |
| `MIDOSIS_JWT_ISSUER`, `MIDOSIS_JWT_AUDIENCIA` | Emisor y audiencia de los tokens (Firebase Authentication) |

## Aislamiento entre comunas

Reforzado en el motor de base de datos, no solo en la consulta de la aplicación
(ADR-011). Toda lectura o escritura pasa por `ContextoComuna`, que fija la comuna en
la transacción; fuera de ella, las políticas no dejan ver ni escribir ninguna fila.

La comuna sale únicamente del token. Si una petición declara otra, por parámetro o
cabecera, se rechaza y queda registrada como evento de seguridad.

La batería `AislamientoEntreComunasTest` es obligatoria en cada compilación, y su
primera prueba verifica que la aplicación no se conecte con un usuario capaz de
saltarse las políticas.

## Claves

Las claves de firma viven en Cloud KMS. En desarrollo se usa un par de prueba
generado localmente, que nunca emite códigos destinados a un paciente real.
