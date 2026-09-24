# Decisiones de arquitectura

Toda decisión estructural queda registrada con su contexto, las alternativas
que se descartaron y las consecuencias que se asumen, incluidas las
desfavorables.

Una decisión no se edita cuando el equipo cambia de opinión: se escribe una
nueva que la reemplaza, y la anterior queda marcada como sustituida.

| ADR | Decisión | Archivo |
|---|---|---|
| ADR-001 | El código QR es autocontenido, no un enlace | [001-el-codigo-qr-es-autocontenido-no-un-enlace.md](001-el-codigo-qr-es-autocontenido-no-un-enlace.md) |
| ADR-002 | Ningún identificador del paciente viaja en el código ni se almacena en el servidor | [002-ningun-identificador-del-paciente-viaja-en-el.md](002-ningun-identificador-del-paciente-viaja-en-el.md) |
| ADR-003 | El contenido del código va cifrado, además de firmado | [003-el-contenido-del-codigo-va-cifrado-ademas-de.md](003-el-contenido-del-codigo-va-cifrado-ademas-de.md) |
| ADR-004 | Orden firmar, comprimir, cifrar | [004-orden-firmar-comprimir-cifrar.md](004-orden-firmar-comprimir-cifrar.md) |
| ADR-005 | Aplicación web en el punto de dispensación | [005-aplicacion-web-en-el-punto-de-dispensacion.md](005-aplicacion-web-en-el-punto-de-dispensacion.md) |
| ADR-006 | Kotlin y Android nativo para la aplicación móvil | [006-kotlin-y-android-nativo-para-la-aplicacion-movil.md](006-kotlin-y-android-nativo-para-la-aplicacion-movil.md) |
| ADR-007 | El motor de planificación se ejecuta en el cliente | [007-el-motor-de-planificacion-se-ejecuta-en-el.md](007-el-motor-de-planificacion-se-ejecuta-en-el.md) |
| ADR-008 | Los datos del tratamiento residen en el dispositivo | [008-los-datos-del-tratamiento-residen-en-el.md](008-los-datos-del-tratamiento-residen-en-el.md) |
| ADR-009 | No integrarse con el sistema de la farmacia; reconocer el producto por lector de código de barras | [009-no-integrarse-con-el-sistema-de-la-farmacia.md](009-no-integrarse-con-el-sistema-de-la-farmacia.md) |
| ADR-010 | La dispensación fraccionada es un dato de primera clase (du, e, co) | [010-la-dispensacion-fraccionada-es-un-dato-de.md](010-la-dispensacion-fraccionada-es-un-dato-de.md) |
| ADR-011 | Aislamiento por comuna reforzado en el motor de base de datos | [011-aislamiento-por-comuna-reforzado-en-el-motor-de.md](011-aislamiento-por-comuna-reforzado-en-el-motor-de.md) |
| ADR-012 | Anti-replay de mejor esfuerzo fuera de línea, efectivo en línea | [012-anti-replay-de-mejor-esfuerzo-fuera-de-linea.md](012-anti-replay-de-mejor-esfuerzo-fuera-de-linea.md) |
| ADR-013 | Claves públicas empaquetadas en la aplicación y actualizables | [013-claves-publicas-empaquetadas-en-la-aplicacion-y.md](013-claves-publicas-empaquetadas-en-la-aplicacion-y.md) |
| ADR-014 | Bitácora de auditoría encadenada por hash | [014-bitacora-de-auditoria-encadenada-por-hash.md](014-bitacora-de-auditoria-encadenada-por-hash.md) |
