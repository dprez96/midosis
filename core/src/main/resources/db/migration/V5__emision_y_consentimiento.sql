-- Registro de cada código emitido (HU-01) y del consentimiento con que se emitió (HU-05).
--
-- Es el «registro de emisión seudonimizado» de la sección 7.7 del informe: no hay ningún
-- dato del paciente, solo lo que el sistema necesita para auditar, revocar (HU-07) y
-- proyectar la demanda (HU-27). El tratamiento completo viaja en el código y en el sobre
-- efímero, no aquí.

CREATE TABLE emision (
    -- El «jti» del código: identifica el tratamiento en todo el sistema.
    id                       CHAR(26)     PRIMARY KEY,
    comuna                   CHAR(5)      NOT NULL,
    punto                    TEXT         NOT NULL,
    emitida_en               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expira_en                TIMESTAMPTZ  NOT NULL,
    -- Quién emitió: el identificador del usuario en el token, no su nombre ni su correo.
    emitida_por              TEXT         NOT NULL,
    -- La clave de la comuna que firmó, para saber qué códigos afecta una revocación de clave.
    kid                      TEXT         NOT NULL,
    -- Consentimiento informado (HU-05, RN-11): qué versión del texto se mostró y cuándo se aceptó.
    consentimiento_version   TEXT         NOT NULL,
    consentimiento_en        TIMESTAMPTZ  NOT NULL,
    -- Por producto: GTIN, duración indicada, dosis entregadas y cobertura (ADR-010).
    productos                JSONB        NOT NULL,
    -- Revocación (HU-07).
    revocada_en              TIMESTAMPTZ,
    revocada_por             TEXT,
    motivo_revocacion        TEXT
);

ALTER TABLE emision ENABLE ROW LEVEL SECURITY;
ALTER TABLE emision FORCE ROW LEVEL SECURITY;

CREATE POLICY aislamiento_comuna ON emision
    USING      (comuna = NULLIF(current_setting('midosis.comuna', true), ''))
    WITH CHECK (comuna = NULLIF(current_setting('midosis.comuna', true), ''));

-- Se emite y se revoca; una emisión no se borra.
GRANT SELECT, INSERT, UPDATE ON emision TO ${rolAplicacion};
