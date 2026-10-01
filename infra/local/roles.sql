-- Usuarios de la base local, como en producción: ni el dueño ni la aplicación son
-- superusuarios, así que el aislamiento entre comunas se aplica a los dos.
CREATE ROLE midosis_dueno LOGIN PASSWORD 'dueno_local' NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;
CREATE ROLE midosis_app LOGIN PASSWORD 'app_local' NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;

ALTER DATABASE midosis OWNER TO midosis_dueno;
ALTER SCHEMA public OWNER TO midosis_dueno;
