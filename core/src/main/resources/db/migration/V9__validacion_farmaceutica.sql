-- Validación farmacéutica de la posología (HU-32, RN-24): quién la validó, cuándo y por qué
-- hizo falta. Queda en el registro de la emisión, que la aplicación no puede editar.
ALTER TABLE emision
    ADD COLUMN validada_por        TEXT,
    ADD COLUMN validada_en         TIMESTAMPTZ,
    ADD COLUMN motivos_validacion  JSONB;
