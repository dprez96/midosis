-- Plantillas de posología frecuente por producto y comuna (HU-03, RF-F-05).
--
-- Las crea el químico farmacéutico de la comuna y las usa el auxiliar en el mesón para no
-- transcribir la posología completa en cada atención. Son contenido clínico de la comuna:
-- igual que el catálogo, cada fila lleva su comuna y la política de seguridad a nivel de
-- fila impide ver o escribir las de otra (ADR-011).
--
-- El identificador es un UUID y no una secuencia: una secuencia compartida dejaría
-- inferir cuántas plantillas crean las demás comunas.

CREATE TABLE plantilla_posologia (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    comuna         CHAR(5)       NOT NULL,
    gtin           VARCHAR(14)   NOT NULL,
    cantidad       NUMERIC(5,2)  NOT NULL CHECK (cantidad > 0),
    unidad         TEXT          NOT NULL,
    -- Intervalo entre tomas en ISO 8601, como la clave «f» del código (motor, Frecuencia).
    frecuencia     TEXT          NOT NULL,
    duracion_dias  INTEGER       CHECK (duracion_dias > 0),
    indicaciones   TEXT,
    -- Quién la creó: el identificador del usuario en el token, no su nombre ni su correo.
    creada_por     TEXT          NOT NULL,
    creada_en      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    FOREIGN KEY (comuna, gtin) REFERENCES producto (comuna, gtin),
    -- Dos plantillas iguales para el mismo producto solo confunden en el mesón.
    UNIQUE NULLS NOT DISTINCT (comuna, gtin, cantidad, unidad, frecuencia, duracion_dias, indicaciones)
);

ALTER TABLE plantilla_posologia ENABLE ROW LEVEL SECURITY;
ALTER TABLE plantilla_posologia FORCE ROW LEVEL SECURITY;

CREATE POLICY aislamiento_comuna ON plantilla_posologia
    USING      (comuna = NULLIF(current_setting('midosis.comuna', true), ''))
    WITH CHECK (comuna = NULLIF(current_setting('midosis.comuna', true), ''));

-- Por ahora se crean y se consultan. Corregir o retirar una plantilla será una acción
-- explícita, con su propio permiso.
GRANT SELECT, INSERT ON plantilla_posologia TO ${rolAplicacion};
