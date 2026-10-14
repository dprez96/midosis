# ADR-016. Formato del sobre: prefijo, COSE etiquetado y claves por función

**Estado:** propuesta

## Contexto

ADR-003, ADR-004 y ADR-013 fijan que el código va firmado, comprimido y cifrado, y que las
claves públicas viajan en la aplicación. Al implementarlo (HU-09, `contrato/esquema/pila-v1.md`)
hubo que decidir lo que esas decisiones no dicen: cómo distingue la aplicación un código de
MiDosis de cualquier otro QR, cuántas claves de cifrado existen y de quién es cada clave de
firma. La sección 7.6.1 del informe pide verificar «prefijo de esquema y versión» antes de
descifrar, sin nombrarlo.

## Decisión

- El texto del QR empieza con `MD1:`: «MD» identifica a MiDosis y el número, la versión de la
  pila. Cabe en el modo alfanumérico del QR.
- COSE_Sign1 y COSE_Encrypt0 van siempre con su etiqueta CBOR (18 y 16), y la lectura la exige.
- La clave de contenido (AES-256-GCM) es **nacional**: una para todas las comunas, con un
  `kid` que nombra su generación.
- Las claves de firma (Ed25519) son **por comuna**: cada una solo firma códigos de su comuna,
  dentro de su vigencia, y puede revocarse.
- Una clave de contenido que la aplicación no conoce se trata como «actualice la aplicación»,
  igual que una versión futura.

## Alternativas descartadas

- Sin prefijo: un QR cualquiera, como el enlace de un sitio web, se rechazaría como «código
  alterado», un mensaje falso que asusta al paciente.
- Una clave de contenido por comuna: su `kid` va a la vista en el sobre y revelaría la comuna,
  justo lo que el cifrado debe ocultar (ADR-004).
- Una sola clave de firma nacional: una filtración comprometería todas las comunas a la vez, y
  la licencia es por comuna.

## Consecuencias

- **Ventaja.** Cada falla tiene un mensaje distinguible, como exige la HU-09.
- **Ventaja.** Revocar o rotar la clave de una comuna no afecta a las demás.
- **Desventaja.** La clave de contenido nacional, extraída de la aplicación, descifra los
  códigos de todas las comunas. Es el alcance ya declarado en ADR-003: el cifrado protege del
  lector genérico, no de quien analiza la aplicación.
- **Desventaja.** El prefijo y los `kid` ocupan bytes del QR: los `kid` de producción deben ser
  cortos.
