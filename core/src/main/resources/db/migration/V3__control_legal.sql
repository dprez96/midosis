-- Productos sujetos a control legal (HU-06, RN-10): estupefacientes del DS 404 y
-- psicotrópicos del DS 405, ambos de 1983. El comprobante impreso omite su nombre,
-- porque es un papel que circula; el contenido cifrado del código sí lo conserva.
--
-- Nulo significa que el producto no está sujeto a control. Las políticas de
-- aislamiento de la tabla siguen rigiendo: la columna nueva no cambia quién ve qué.

ALTER TABLE producto
    ADD COLUMN control_legal TEXT NULL
        CONSTRAINT producto_control_legal_valido CHECK (control_legal IN ('DS404', 'DS405'));
