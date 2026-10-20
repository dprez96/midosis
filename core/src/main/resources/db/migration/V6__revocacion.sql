-- Revocación de un código emitido por error (HU-07).
--
-- El sobre del canje con código corto queda ligado a su emisión, para que revocarla lo
-- destruya: así el código deja de poder canjearse en línea. Revocar no borra la emisión, la
-- marca (columnas revocada_* de V5), y queda quién, cuándo y por qué.

ALTER TABLE sobre ADD COLUMN emision CHAR(26);
CREATE INDEX sobre_por_emision ON sobre (emision);
