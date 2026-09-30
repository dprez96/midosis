-- Catálogo de productos por comuna y registro de eventos de seguridad.
--
-- El aislamiento entre comunas se refuerza en el motor de base de datos (ADR-011):
-- cada fila lleva su comuna y una política de seguridad a nivel de fila impide ver o
-- escribir filas de otra, aunque la aplicación tenga un error.
--
-- La comuna activa viaja en la variable de sesión midosis.comuna, que la aplicación
-- fija por transacción. Si no está fijada, current_setting devuelve NULL, la
-- comparación nunca es verdadera y la política no deja pasar nada: falla cerrada.
--
-- Esta migración la ejecuta el usuario dueño. La aplicación se conecta con otro,
-- ${rolAplicacion}, sin privilegios de dueño: el dueño de una tabla y los
-- superusuarios se saltan las políticas.

CREATE TABLE producto (
    comuna            CHAR(5)      NOT NULL,
    gtin              VARCHAR(14)  NOT NULL,
    nombre            TEXT         NOT NULL,
    principio_activo  TEXT         NOT NULL,
    forma             TEXT         NOT NULL,
    concentracion     TEXT         NOT NULL,
    PRIMARY KEY (comuna, gtin)
);

ALTER TABLE producto ENABLE ROW LEVEL SECURITY;
-- FORCE la aplica también al dueño, como segunda barrera.
ALTER TABLE producto FORCE ROW LEVEL SECURITY;

CREATE POLICY aislamiento_comuna ON producto
    USING      (comuna = NULLIF(current_setting('midosis.comuna', true), ''))
    WITH CHECK (comuna = NULLIF(current_setting('midosis.comuna', true), ''));

-- Intentos de cruzar comunas y otros hechos de seguridad. La aplicación puede
-- registrar eventos de su propia comuna, pero no leerlos, cambiarlos ni borrarlos:
-- los revisa el proveedor.
CREATE TABLE evento_seguridad (
    id        BIGSERIAL    PRIMARY KEY,
    instante  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    comuna    CHAR(5)      NOT NULL,
    tipo      TEXT         NOT NULL,
    detalle   TEXT         NOT NULL
);

ALTER TABLE evento_seguridad ENABLE ROW LEVEL SECURITY;
ALTER TABLE evento_seguridad FORCE ROW LEVEL SECURITY;

CREATE POLICY registrar_evento_propio ON evento_seguridad FOR INSERT
    WITH CHECK (comuna = NULLIF(current_setting('midosis.comuna', true), ''));

GRANT SELECT, INSERT, UPDATE, DELETE ON producto TO ${rolAplicacion};
GRANT INSERT ON evento_seguridad TO ${rolAplicacion};
GRANT USAGE ON SEQUENCE evento_seguridad_id_seq TO ${rolAplicacion};
