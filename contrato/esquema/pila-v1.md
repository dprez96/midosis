# Pila del código, versión 1

Cómo se convierte la carga útil ([carga-v1.md](carga-v1.md)) en el texto del QR y
cómo se lee de vuelta sin conexión. El orden de las capas está justificado en
ADR-004 y el de la lectura sigue la sección 7.6.1 del informe.

```
carga útil (CBOR determinista)
  -> COSE_Sign1, Ed25519           firma de la comuna emisora
  -> zlib                          compresión
  -> COSE_Encrypt0, A256GCM        sobre cifrado
  -> Base45                        texto del modo alfanumérico del QR
  -> «MD1:» + texto                prefijo de esquema y versión de la pila
```

La implementación de referencia está en `herramientas/pila.py` y la de Kotlin, que
comparten core y paciente, en `kotlin/`.

## Capas

### Prefijo

`MD1:` identifica un código de MiDosis y la versión de esta pila. Sirve para
distinguir un QR cualquiera de un código dañado, que merecen mensajes distintos, y
cabe en el modo alfanumérico. Una versión futura de la pila usa `MD2:`, `MD3:`, etc.

### COSE_Sign1 (RFC 9052)

- Con la etiqueta CBOR 18.
- Cabecera protegida: exactamente `{1: -8, 4: kid}`, es decir, EdDSA y el
  identificador de la clave, en bytes UTF-8. Al ir protegido, el `kid` queda
  cubierto por la firma.
- Cabecera libre: un mapa, que la lectura ignora. Quien emite lo deja vacío.
- La carga va dentro del mensaje, no separada. La firma son 64 bytes (RFC 8032) sobre
  la `Sig_structure` sin datos externos.

### zlib

RFC 1950, con el nivel de compresión máximo. Al leer se descomprime **con un tope de
8 KiB**: unos cien bytes bien elegidos se expanden a megas. La carga más grande
admitida, de veinte productos, firmada ocupa unos 2,3 KiB. Se rechazan también los
datos que sobren después del final del bloque.

La salida de zlib puede variar entre bibliotecas, así que las pruebas no comparan
esta capa byte a byte: comparan la firma y el cifrado, que sí son deterministas.

### COSE_Encrypt0 (RFC 9052)

- Con la etiqueta CBOR 16.
- Cabecera protegida: exactamente `{1: 3}`, AES-256-GCM. Va en la `Enc_structure`
  como dato autenticado.
- Cabecera libre: exactamente `{4: kid, 5: iv}`. El `kid` nombra la clave de
  contenido y el IV son 12 bytes aleatorios, nuevos en cada emisión.
- El texto cifrado lleva al final la etiqueta de autenticación de 16 bytes.

La clave de contenido es **nacional**: la misma para todas las comunas. Si hubiera
una por comuna, su `kid`, que va a la vista, revelaría la comuna, que es justo lo
que el cifrado debe ocultar (ADR-004). Viaja dentro de la aplicación, así que protege
del lector genérico y no de quien la extraiga: es el alcance declarado en ADR-003.

### Base45 (RFC 9285)

La lectura es estricta: rechaza caracteres fuera del alfabeto, largos que dejan un
carácter suelto y grupos cuyo valor no cabe en los bytes que representan.

## Claves

Cada **clave de firma** pertenece a una comuna y tiene identificador, período de
validez y estado (sección 7.6.2). La aplicación trae empaquetadas las vigentes y
conserva las antiguas, para seguir leyendo códigos emitidos antes de una rotación
(ADR-013). Una clave:

- solo firma códigos de su comuna: `cm` debe coincidir;
- solo firma códigos emitidos dentro de su vigencia: `vigenteDesde ≤ iat` y, si
  tiene término, `iat < vigenteHasta`;
- deja de servir si se revoca.

En core, la clave privada vive en Cloud KMS y nunca sale de ahí: la emisión recibe un
`Firmante`, no la clave.

### Claves de prueba

`vectores/claves-de-prueba.json` trae las claves con que se generaron los vectores.
**No protegen nada**: cada una es el SHA-256 de una frase escrita en el mismo archivo,
y por eso pueden estar en el repositorio. Sus `kid` empiezan con `prueba-`, y la
aplicación de producción se niega a cargar un conjunto de claves que contenga alguno.

## Lectura y rechazos

La lectura aplica los pasos en este orden y se detiene en el primero que falla. Los
motivos de la carga útil (`estructura-invalida`, `version-no-soportada`,
`contenido-incoherente`) se explican en carga-v1.md.

| Paso | Motivo | Regla | Falla cuando |
|---|---|---|---|
| 1 | `no-es-de-midosis` | `prefijo` | El texto no empieza con `MD`, un número y dos puntos. |
| 2 | `version-no-soportada` | `prefijo` | El prefijo no es `MD1:`. |
| 3 | `alterado` | `base45` | El resto no es Base45 válido. |
| 4 | `alterado` | `cose` | El sobre no es un COSE_Encrypt0 etiquetado y bien formado. |
| 5 | `alterado` | `algoritmo` | La cabecera protegida del sobre no es `{1: 3}`. |
| 6 | `version-no-soportada` | `clave-de-contenido` | La aplicación no trae la clave de contenido del `kid`: la emitió una versión más nueva del sistema. |
| 7 | `alterado` | `cifrado` | Falla la etiqueta de autenticación de AES-GCM. |
| 8 | `alterado` | `descompresion` | zlib inválido, más de 8 KiB o datos sobrantes. |
| 9 | `alterado` | `cose` | Lo descomprimido no es un COSE_Sign1 etiquetado y bien formado. |
| 10 | `alterado` | `algoritmo` | La firma no declara EdDSA. |
| 11 | `emisor-desconocido` | `clave-desconocida` | La aplicación no conoce el `kid` de la firma. |
| 12 | `emisor-desconocido` | `clave-revocada` | La clave está revocada. |
| 13 | `alterado` | `firma` | La firma no es válida. |
| 14 | los de la carga útil | | La carga no cumple carga-v1.md. |
| 15 | `emisor-desconocido` | `comuna-de-la-clave` | La clave es de otra comuna. |
| 16 | `emisor-desconocido` | `clave-fuera-de-vigencia` | `iat` cae fuera de la vigencia de la clave. |
| 17 | `expirado` | `vigencia` | La hora del teléfono es igual o posterior a `exp`. |

La reutilización, el último paso de la sección 7.6.1, la resuelve quien guarda el
tratamiento, con el registro local de `jti` ya cargados.

Para el paciente, los motivos se traducen en mensajes distintos: «este código fue
alterado», «no proviene de una farmacia reconocida», «venció» o «actualice la
aplicación». La regla queda para el registro y las pruebas.

## Tamaño

El código del ejemplo del informe ocupa 540 caracteres con las claves de prueba. Con
corrección de errores M cabe en un QR versión 15, de 77 módulos: unos 31 mm a 0,40 mm
por módulo, dentro de lo que pide la tabla 87 (25 mm o más). Los `kid` de producción
deben ser cortos: cada byte cuenta.
