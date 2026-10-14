-- Sobres para el canje con código corto y PIN (HU-10, tabla 88 del informe).
--
-- Cuando el QR no se puede leer, el paciente escribe el código corto del comprobante y su
-- PIN, y la aplicación descarga el código completo. El sobre guarda ese código tal como se
-- imprime en el QR: va cifrado con la clave de contenido, así que la base no ve la
-- posología. Es efímero: se borra al canjearse y vence a las 72 horas (RN-03, sección 7.7).
--
-- No hay ningún dato del paciente. El PIN se guarda derivado con PBKDF2, nunca en claro.

CREATE TABLE sobre (
    codigo_corto      CHAR(10)     PRIMARY KEY,
    comuna            CHAR(5)      NOT NULL,
    codigo            TEXT         NOT NULL,
    pin_derivado      BYTEA        NOT NULL,
    pin_sal           BYTEA        NOT NULL,
    expira_en         TIMESTAMPTZ  NOT NULL,
    -- Limitación de tasa: 5 intentos por código en 10 minutos y bloqueo tras 10 fallidos.
    intentos_ventana  INTEGER      NOT NULL DEFAULT 0,
    ventana_desde     TIMESTAMPTZ,
    fallidos          INTEGER      NOT NULL DEFAULT 0,
    creado_en         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

ALTER TABLE sobre ENABLE ROW LEVEL SECURITY;
ALTER TABLE sobre FORCE ROW LEVEL SECURITY;

-- La comuna emisora ve y escribe solo sus sobres, como en el resto de las tablas (ADR-011).
CREATE POLICY aislamiento_comuna ON sobre
    USING      (comuna = NULLIF(current_setting('midosis.comuna', true), ''))
    WITH CHECK (comuna = NULLIF(current_setting('midosis.comuna', true), ''));

-- El canje lo pide el paciente, sin cuenta ni comuna. La transacción declara el código corto
-- que intenta canjear y esta política expone únicamente esa fila: no se puede listar ni
-- recorrer los sobres, ni siquiera los de la misma comuna.
CREATE POLICY canje_del_codigo ON sobre FOR SELECT
    USING (codigo_corto = NULLIF(current_setting('midosis.canje', true), ''));
CREATE POLICY canje_registra_intentos ON sobre FOR UPDATE
    USING      (codigo_corto = NULLIF(current_setting('midosis.canje', true), ''))
    WITH CHECK (codigo_corto = NULLIF(current_setting('midosis.canje', true), ''));
CREATE POLICY canje_destruye_el_sobre ON sobre FOR DELETE
    USING (codigo_corto = NULLIF(current_setting('midosis.canje', true), ''));

GRANT SELECT, INSERT, UPDATE, DELETE ON sobre TO ${rolAplicacion};
