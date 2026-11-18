# Core en Cloud Run

Cómo se publica `core/` en el proyecto de desarrollo y QA (`midosis-desarrollo`), en
`southamerica-west1`. Solo datos sintéticos.

| Recurso | Nombre | Para qué |
|---|---|---|
| Cloud SQL | `midosis-db`, PostgreSQL 16, `db-f1-micro`, respaldo diario y recuperación a un punto de 7 días | La base de core |
| Cuenta de servicio | `midosis-core@midosis-desarrollo.iam.gserviceaccount.com` | Identidad de core: cliente de Cloud SQL, firma con KMS y lee sus secretos |
| Cloud KMS | llavero `midosis-firma`, clave `firma-13123` | Firmar los códigos de la comuna (ADR-016) |
| Secret Manager | `midosis-clave-contenido`, `midosis-db-dueno-clave`, `midosis-db-app-clave`, `midosis-db-postgres-clave` | Clave de contenido y contraseñas de la base |
| Artifact Registry | `midosis` | Imágenes de core |
| Cloud Run | `midosis-core`, de 0 a 2 instancias | El servicio |

## Pasos

Con `gcloud` con sesión iniciada en la cuenta dueña del proyecto y Docker abierto:

```bash
PROYECTO=midosis-desarrollo CUENTA_CORE=midosis-core@midosis-desarrollo.iam.gserviceaccount.com \
  infra/kms/crear-claves.sh 13123
PROYECTO=midosis-desarrollo infra/cloudrun/preparar-base.sh
PROYECTO=midosis-desarrollo infra/cloudrun/desplegar.sh
```

- `preparar-base.sh` crea las contraseñas directo en Secret Manager, la base `midosis` y los
  usuarios `midosis_dueno` y `midosis_app` con `roles.sql`. Ninguno es superusuario ni tiene
  `BYPASSRLS`, como en el equipo: el aislamiento entre comunas se aplica a los dos (ADR-011).
  Para correr `psql` autoriza la IP de este equipo en la instancia y la retira al terminar.
- `desplegar.sh` compila el jar, arma la imagen en Cloud Build y publica el servicio. Las
  migraciones de Flyway corren al iniciar core, con el usuario dueño.

Los scripts son idempotentes y nunca imprimen una contraseña.

## Por qué así

- **Conector de Cloud SQL** (`postgres-socket-factory`): core se conecta con la identidad de
  su cuenta de servicio, sin IP autorizadas ni red privada. Una red privada con conector de
  VPC cuesta más y no se justifica en desarrollo (11.6 del informe).
- **Acceso público al servicio**: la web y la aplicación lo llaman desde Internet. La API
  exige el token de Firebase en todo, salvo el canje y la consulta de estado, que la usa el
  paciente sin cuenta (ADR-017).
- **El jar se compila en el equipo**: core depende de `motor` y `contrato` por composite build.
