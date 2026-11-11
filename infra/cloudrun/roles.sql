-- Usuarios de la base en Cloud SQL, como en el equipo (infra/local/roles.sql): ni el dueño ni la
-- aplicación son superusuarios ni pueden saltarse la seguridad a nivel de fila, así que el
-- aislamiento entre comunas se aplica a los dos (ADR-011).
--
-- Lo corre preparar-base.sh con el usuario «postgres» de Cloud SQL, conectado a la base
-- «midosis». Las contraseñas llegan por variables de entorno y nunca se imprimen.
-- Es idempotente: si los usuarios existen, solo actualiza sus contraseñas y atributos.

\set ON_ERROR_STOP on
\getenv clave_dueno CLAVE_DUENO
\getenv clave_app CLAVE_APP

SELECT NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'midosis_dueno') AS crear_dueno \gset
\if :crear_dueno
CREATE ROLE midosis_dueno LOGIN;
\endif
ALTER ROLE midosis_dueno LOGIN PASSWORD :'clave_dueno' NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;

SELECT NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'midosis_app') AS crear_app \gset
\if :crear_app
CREATE ROLE midosis_app LOGIN;
\endif
ALTER ROLE midosis_app LOGIN PASSWORD :'clave_app' NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;

-- Para traspasar la base y el esquema, «postgres» tiene que ser miembro del nuevo dueño.
GRANT midosis_dueno TO postgres;
ALTER DATABASE midosis OWNER TO midosis_dueno;
ALTER SCHEMA public OWNER TO midosis_dueno;

-- Verificación: ninguno de los dos se salta las políticas de fila.
SELECT rolname, rolsuper, rolbypassrls FROM pg_roles WHERE rolname IN ('midosis_dueno', 'midosis_app') ORDER BY rolname;
