-- Usuario con el que se conecta la aplicación en las pruebas. Igual que en producción:
-- sin privilegios de dueño, sin superusuario y sin permiso para saltarse las políticas
-- de seguridad a nivel de fila. Las tablas las crea el superusuario del contenedor.
CREATE ROLE midosis_app LOGIN PASSWORD 'app_pruebas' NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;
