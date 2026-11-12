-- Usuarios de la base en Cloud SQL, como en el equipo (infra/local/roles.sql): ni el dueño ni la
-- aplicación son superusuarios ni pueden saltarse la seguridad a nivel de fila, así que el
-- aislamiento entre comunas se aplica a los dos (ADR-011).
--
-- Lo corre preparar-base.sh con el usuario «postgres» de Cloud SQL, conectado a la base
-- «midosis». Las contraseñas llegan por variables de entorno y nunca se imprimen.
-- Es idempotente: si los usuarios existen, solo actualiza sus contraseñas.
--
-- En Cloud SQL «postgres» no es superusuario, y PostgreSQL solo deja a un superusuario
-- escribir SUPERUSER o BYPASSRLS, aunque sea para negarlos. No hace falta: un usuario nuevo
-- nace sin ninguno de los dos. La consulta del final lo verifica.

\set ON_ERROR_STOP on
\getenv clave_dueno CLAVE_DUENO
\getenv clave_app CLAVE_APP

SELECT NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'midosis_dueno') AS crear_dueno \gset
\if :crear_dueno
CREATE ROLE midosis_dueno LOGIN;
\endif
ALTER ROLE midosis_dueno LOGIN PASSWORD :'clave_dueno' NOCREATEDB NOCREATEROLE;

SELECT NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'midosis_app') AS crear_app \gset
\if :crear_app
CREATE ROLE midosis_app LOGIN;
\endif
ALTER ROLE midosis_app LOGIN PASSWORD :'clave_app' NOCREATEDB NOCREATEROLE;

-- Para traspasar la base y el esquema, «postgres» tiene que ser miembro del nuevo dueño.
GRANT midosis_dueno TO postgres;
ALTER DATABASE midosis OWNER TO midosis_dueno;
ALTER SCHEMA public OWNER TO midosis_dueno;

-- Verificación: ninguno de los dos es superusuario ni se salta las políticas de fila. Si
-- alguno lo fuera, el script se detiene con error.
SELECT rolname, rolsuper, rolbypassrls FROM pg_roles WHERE rolname IN ('midosis_dueno', 'midosis_app') ORDER BY rolname;
SELECT count(*) = 0 AS sin_privilegios FROM pg_roles
WHERE rolname IN ('midosis_dueno', 'midosis_app') AND (rolsuper OR rolbypassrls) \gset
\if :sin_privilegios
\else
\echo 'ERROR: midosis_dueno o midosis_app puede saltarse la seguridad por fila'
SELECT 1/0;
\endif
