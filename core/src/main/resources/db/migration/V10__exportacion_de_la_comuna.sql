-- Exportación de los datos de la comuna (HU-29, RF-B-12, RN-20): el administrador comunal lee
-- la auditoría de su comuna para entregarla en formato abierto. Solo lectura y solo la propia
-- comuna; los eventos siguen sin poder editarse ni borrarse desde la aplicación.
CREATE POLICY leer_evento_propio ON evento_seguridad FOR SELECT
    USING (comuna = NULLIF(current_setting('midosis.comuna', true), ''));

GRANT SELECT ON evento_seguridad TO ${rolAplicacion};
