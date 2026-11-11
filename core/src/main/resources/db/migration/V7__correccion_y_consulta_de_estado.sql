-- Corrección de un tratamiento ya cargado (HU-33, RN-25) y consulta de su estado desde la
-- aplicación del paciente (RF-B-15, RF-M-30).
--
-- El químico farmacéutico corrige un código emitiendo uno de reemplazo, que trae en «rp» el jti
-- del original. Esta tabla une ambos y guarda quién corrigió, cuándo, por qué y qué debe hacer
-- el paciente mientras tanto: seguir con el tratamiento anterior o suspenderlo. Una emisión se
-- corrige una sola vez; para cambiar de nuevo, se corrige el reemplazo.

CREATE TABLE correccion (
    original       CHAR(26)     PRIMARY KEY REFERENCES emision (id),
    reemplazo      CHAR(26)     NOT NULL UNIQUE REFERENCES emision (id),
    comuna         CHAR(5)      NOT NULL,
    motivo         TEXT         NOT NULL,
    instruccion    TEXT         NOT NULL CHECK (instruccion IN ('continuar', 'suspender')),
    -- El identificador del usuario en el token, no su nombre.
    corregida_por  TEXT         NOT NULL,
    corregida_en   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (original <> reemplazo)
);

ALTER TABLE correccion ENABLE ROW LEVEL SECURITY;
ALTER TABLE correccion FORCE ROW LEVEL SECURITY;

CREATE POLICY aislamiento_comuna ON correccion
    USING      (comuna = NULLIF(current_setting('midosis.comuna', true), ''))
    WITH CHECK (comuna = NULLIF(current_setting('midosis.comuna', true), ''));

-- La consulta de estado la hace el paciente, sin cuenta ni comuna, como el canje. La
-- transacción declara en midosis.consulta el jti que pregunta, y estas políticas exponen
-- únicamente esa emisión y su corrección: no se puede listar ni recorrer nada más.
CREATE POLICY consulta_de_estado ON emision FOR SELECT
    USING (id = NULLIF(current_setting('midosis.consulta', true), ''));
CREATE POLICY consulta_de_estado ON correccion FOR SELECT
    USING (original = NULLIF(current_setting('midosis.consulta', true), ''));

-- Una corrección no se edita ni se borra: es parte de la auditoría.
GRANT SELECT, INSERT ON correccion TO ${rolAplicacion};
