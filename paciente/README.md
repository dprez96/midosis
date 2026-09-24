# MiDosis Paciente

Aplicación móvil. Kotlin y Android nativo, desde Android 9.

Nativa y no multiplataforma por decisión explícita (ADR-006): el mayor riesgo
técnico del proyecto está en las alarmas exactas y en el comportamiento de los
administradores de batería de cada fabricante, y ahí conviene el acceso directo a
`AlarmManager` sin una capa intermedia.

## Lo que nunca sale del teléfono

El motor de planificación se ejecuta en el dispositivo (ADR-007) y los datos del
tratamiento residen en él (ADR-008), cifrados con SQLCipher y con la clave en el
almacén de claves de Android. Las preferencias horarias del paciente, que son lo
más revelador de su vida privada, no se transmiten a ningún servidor.

## Banco de dispositivos

Las alarmas se verifican en dispositivos físicos de al menos tres fabricantes
distintos durante 30 días. El emulador no sirve para esto: no reproduce los
administradores de batería propietarios.
