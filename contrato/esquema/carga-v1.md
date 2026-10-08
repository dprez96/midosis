# Carga útil, versión 1

La estructura está en [`carga-v1.cddl`](carga-v1.cddl), que es normativa. Este
documento explica cada clave y fija las reglas que CDDL no puede expresar. Parte
de la sección 7.5.1 del informe de arquitectura.

## Codificación

- CBOR (RFC 8949). Quien emite usa la **codificación determinista** de la sección
  4.2.1: enteros en su forma más corta, longitudes definidas y claves ordenadas.
  Así los mismos datos producen siempre los mismos bytes.
- Quien lee acepta un solo elemento y nada después, y rechaza los mapas con claves
  repetidas.
- Los mapas son cerrados: una clave desconocida invalida la carga. Ningún dato que
  no esté en el esquema puede viajar en el código (RNF-13, ADR-002).

## Raíz

| Clave | Tipo | Significado |
|---|---|---|
| `v` | 1 | Versión del esquema. Se lee antes que todo lo demás. |
| `cm` | 5 dígitos | Comuna emisora, según el código único territorial del INE. |
| `iss` | `CL-FP-ccccc-nn` | Punto de dispensación que emite. Es lo que la aplicación muestra antes de aceptar el tratamiento. |
| `jti` | ULID | Identificador del tratamiento: 26 caracteres en Crockford Base32. Es la clave con que se detecta la reutilización. |
| `iat` | entero | Emisión, en segundos desde 1970 (UTC). |
| `exp` | entero | Fin de la vigencia del canje (RN-03). |
| `rx` | 1 a 20 productos | Los productos del tratamiento. |
| `rp` | ULID, opcional | Tratamiento que este código reemplaza. Solo en los códigos de corrección (RN-25). |

## Cada producto

| Clave | Tipo | Significado |
|---|---|---|
| `c` | GTIN | Código del producto. La aplicación lo resuelve contra su catálogo local. |
| `n` | texto, hasta 40 bytes | Etiqueta corta, para cuando el GTIN no está en el catálogo del teléfono. Viaja aunque el producto sea de control legal: lo que se omite es el nombre impreso en el comprobante (HU-06). |
| `a` | texto, hasta 60 bytes | Principio activo, con su denominación común. |
| `d` | texto, hasta 40 bytes | Dosis por toma, como se indicó: «1 comprimido», «10 UI». |
| `f` | duración ISO 8601 | Intervalo entre tomas, en forma canónica (ver más abajo). |
| `du` | 1 a 365 | Duración indicada por el médico, en días. |
| `e` | 1 a 9999 | Dosis entregadas en este retiro, **contadas en tomas**: dos inhalaciones por toma y 200 inhalaciones entregadas son `e = 100`. |
| `co` | 0 a 9999 | Días que cubre lo entregado. Es redundante a propósito: quien lee lo recalcula y debe coincidir. |
| `r` | código | Vía de administración. |
| `o` | 1 a 5 códigos, opcional | Observaciones de administración. |
| `nt` | texto, hasta 240 bytes, opcional | Nota del químico farmacéutico. |

`du`, `e` y `co` son tres valores distintos (ADR-010): lo indicado, lo entregado y
lo que alcanza a cubrir.

### Intervalo

Solo horas y minutos, sin días, semanas ni meses, sin componentes en cero y sin
decimales: `PT12H`, `PT24H`, `PT1H30M`, `PT45M`. Un día es `PT24H`. Es la forma que
produce `Duration.toString()` en Java y la que ya entrega core. El motor acepta
otras escrituras, como `P1D`, pero el contrato no.

### Textos

Los límites se cuentan en **bytes UTF-8**, no en caracteres, porque así se cuentan
igual en Kotlin, TypeScript y Python. Una letra con tilde ocupa dos bytes. Core
limita las indicaciones a 120 caracteres, que en español caben siempre en 240 bytes.

### Vías de administración

| Código | Vía |
|---|---|
| `VO` | oral |
| `SL` | sublingual |
| `TOP` | tópica, sobre la piel |
| `TD` | transdérmica, con parche |
| `OFT` | oftálmica |
| `OTI` | ótica |
| `NAS` | nasal |
| `INH` | inhalatoria |
| `REC` | rectal |
| `VAG` | vaginal |
| `SC` | subcutánea |
| `IM` | intramuscular |

### Observaciones

| Código | Observación |
|---|---|
| 1 | en ayunas |
| 2 | con alimentos |
| 3 | al acostarse |
| 4 | no partir ni masticar |
| 5 | agitar antes de usar |

El informe fija solo el 2. El resto de esta tabla y la de vías **esperan el visto
bueno del químico farmacéutico asesor**: son contenido clínico. Se pueden ajustar
sin cambiar de versión mientras no se haya emitido ningún código real.

## Lectura y rechazos

Quien lee aplica los pasos en este orden y se detiene en el primero que falla.
Cada rechazo tiene un **motivo**, que decide el mensaje al paciente, y una
**regla**, que dice qué falló. Los nombres son los mismos en todos los módulos y
en los vectores.

| Paso | Motivo | Regla | Falla cuando |
|---|---|---|---|
| 1 | `estructura-invalida` | `cbor-mal-formado` | Los bytes no son un elemento CBOR completo, sobran bytes o un mapa repite una clave. |
| 2 | `estructura-invalida` | `no-es-mapa` | La raíz no es un mapa. |
| 3 | `estructura-invalida` | `sin-version` | Falta `v` o no es un entero sin signo. |
| 4 | `version-no-soportada` | `version` | `v` no es una versión que esta aplicación conozca. Se pide actualizarla. |
| 5 | `estructura-invalida` | `esquema` | No cumple `carga-v1.cddl`. |
| 6 | `contenido-incoherente` | `comuna-del-emisor` | La comuna dentro de `iss` no es `cm`. |
| 7 | `contenido-incoherente` | `vigencia` | No se cumple `iat < exp ≤ iat + 72 horas`. |
| 8 | `contenido-incoherente` | `reemplazo-distinto` | `rp` es igual a `jti`. |
| 9 | `contenido-incoherente` | `gtin-digito-verificador` | El dígito verificador del GTIN no cuadra. |
| 10 | `contenido-incoherente` | `intervalo-en-rango` | `f` es menor que 30 minutos o mayor que 90 días, los límites del motor. |
| 11 | `contenido-incoherente` | `cobertura` | `co` no es `piso(e × f / 1 día)`. |
| 12 | `contenido-incoherente` | `observaciones-ordenadas` | `o` no va en orden ascendente sin repetir. |
| 13 | `contenido-incoherente` | `observaciones-compatibles` | `o` trae a la vez «en ayunas» y «con alimentos». |
| 14 | `contenido-incoherente` | `sin-caracteres-de-control` | `n`, `a`, `d` o `nt` traen un carácter de control Unicode (categoría Cc). |

Las reglas 9 a 14 se aplican a cada producto, en el orden de `rx`.

Esta lectura viene después de verificar la firma: un código que llega aquí lo
emitió una farmacia habilitada, así que un rechazo delata un error del emisor y no
un ataque. La expiración respecto de la hora actual, la firma y el emisor
desconocido son de la HU-09 y tienen sus propios motivos.

## Diferencias con el ejemplo del informe

- El `jti` del ejemplo, `01JB7K3QW9XZ4M2N`, tiene 16 caracteres y un ULID tiene 26.
  El esquema exige los 26.
- El ejemplo escribe «Losartan» e «indicacion» sin tildes. Los vectores usan las
  tildes: cuestan un byte cada una y el paciente lee la etiqueta.
- El informe no fija los códigos de vía ni la lista de observaciones, ni los
  largos de `n`, `a` y `d`. Están definidos aquí.
