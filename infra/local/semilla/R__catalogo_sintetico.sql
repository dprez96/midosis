-- Catálogo sintético para desarrollo. Productos genéricos con GTIN inventados, de dígito
-- verificador correcto, en la comuna de los usuarios de prueba (13123). Nunca datos
-- reales de una farmacia.
--
-- Lo aplica core con el perfil «local» (application-local.yaml), después de las
-- migraciones. Las políticas de aislamiento también rigen para el dueño de las tablas,
-- así que primero se fija la comuna en la transacción.

SELECT set_config('midosis.comuna', '13123', true);

INSERT INTO producto (comuna, gtin, nombre, principio_activo, forma, concentracion) VALUES
    ('13123', '7802250012344', 'Losartán 50 mg', 'Losartán potásico', 'Comprimido recubierto', '50 mg'),
    ('13123', '7802250012405', 'Metformina 850 mg', 'Metformina clorhidrato', 'Comprimido', '850 mg'),
    ('13123', '7802250012504', 'Atorvastatina 20 mg', 'Atorvastatina cálcica', 'Comprimido recubierto', '20 mg'),
    ('13123', '7802250012603', 'Enalapril 10 mg', 'Enalapril maleato', 'Comprimido', '10 mg'),
    ('13123', '7802250012702', 'Amlodipino 5 mg', 'Amlodipino besilato', 'Comprimido', '5 mg'),
    ('13123', '7802250012801', 'Omeprazol 20 mg', 'Omeprazol', 'Cápsula con microgránulos', '20 mg'),
    ('13123', '7802250012900', 'Levotiroxina 100 mcg', 'Levotiroxina sódica', 'Comprimido', '100 mcg'),
    ('13123', '7802250013006', 'Ácido acetilsalicílico 100 mg', 'Ácido acetilsalicílico', 'Comprimido', '100 mg'),
    ('13123', '7802250013105', 'Salbutamol 100 mcg', 'Salbutamol sulfato', 'Aerosol para inhalación', '100 mcg/dosis'),
    ('13123', '7802250013204', 'Paracetamol 500 mg', 'Paracetamol', 'Comprimido', '500 mg')
ON CONFLICT (comuna, gtin) DO NOTHING;
