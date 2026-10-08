# Contrato de la credencial

Esta carpeta define el formato del código de tratamiento. Es la única fuente de
verdad: `core/` lo emite, `paciente/` lo consume y `farmacia/` lo imprime, y los
tres validan contra los mismos vectores de prueba.

## Pila de codificación

El orden no es arbitrario y está justificado en ADR-004:

```
carga útil (CBOR)
  -> firma        COSE_Sign1 con Ed25519
  -> compresión   zlib
  -> cifrado      COSE_Encrypt0 con AES-256-GCM
  -> texto        Base45, con el prefijo «MD1:»
  -> impresión    QR, ISO/IEC 18004
```

Se firma antes de cifrar para que el emisor no quede a la vista de un lector
genérico, y se comprime antes de cifrar porque después del cifrado no hay
redundancia que comprimir.

## Qué hay aquí

| Archivo | Qué es |
|---|---|
| `esquema/carga-v1.cddl` | La estructura de la carga útil, en CDDL (RFC 8610). Es normativa. |
| `esquema/carga-v1.md` | Qué significa cada clave y las reglas que CDDL no puede expresar. |
| `esquema/pila-v1.md` | Las capas de la pila, las claves y el orden de la lectura, con sus motivos de rechazo. |
| `kotlin/` | La implementación que comparten core y paciente (módulo `credencial`). |
| `herramientas/` | La implementación de referencia en Python, que genera los vectores. |
| `vectores/` | Los casos de prueba. |

### El módulo `credencial`

Kotlin puro con bytecode de Java 17, como el motor: core y paciente lo compilan desde
su código fuente con `includeBuild("../contrato/kotlin")`. Expone `EmisorDeCodigos`,
`LectorDeCodigos`, `CodecDeCarga` y las clases de claves. Se prueba con
`./gradlew build` dentro de `contrato/kotlin`, contra todos los vectores.

## Vectores de prueba

`vectores/` contiene casos con entrada y resultado esperado. Cada módulo ejecuta
la misma batería. Un cambio de formato que rompa la compatibilidad hace fallar las
pruebas de los tres módulos a la vez, que es justamente lo que se busca.

Convención de nombres:

```
vectores/valido-losartan-fraccionado.json
vectores/invalido-firma-alterada.json
vectores/limite-plan-maximo.json
```

Hay cuatro clases de archivos:

**Carga útil** (`valido-*`, `invalido-*`, `limite-*`). Un caso por archivo:

```json
{
  "descripcion": "Qué prueba el caso.",
  "carga": { "v": 1, "...": "..." },
  "cbor": "a7617601...",
  "bytes": 226,
  "resultado": { "valido": false, "motivo": "contenido-incoherente", "regla": "cobertura" }
}
```

- `cbor` es la entrada de verdad, en hexadecimal. Las pruebas leen esos bytes, no
  `carga`, que es solo para leer el caso y falta cuando los bytes no forman CBOR.
- `resultado` es `{"valido": true}` o trae el `motivo` y la `regla` del rechazo,
  definidos en `esquema/carga-v1.md`. Un módulo debe rechazar con el mismo motivo;
  comprobar también la regla es recomendable.

**Código completo** (`codigo-*`). El texto del QR, el instante en que se lee y el
resultado esperado. `capas` trae cada capa en hexadecimal (carga, firmado,
comprimido, IV y cifrado) para depurar y para comparar la firma y el cifrado byte a
byte; la compresión no se compara, porque cada biblioteca de zlib puede producir
bytes distintos.

```json
{
  "descripcion": "Firmado con una clave que la aplicación conoce, pero marcada como revocada.",
  "ahora": 1786986000,
  "codigo": "MD1:6BF...",
  "caracteres": 540,
  "capas": { "carga": "a7...", "firmado": "d2...", "comprimido": "78...", "iv": "...", "cifrado": "d0..." },
  "resultado": { "valido": false, "motivo": "emisor-desconocido", "regla": "clave-revocada" }
}
```

**Claves de prueba** (`claves-de-prueba.json`). Las claves con que se generaron los
vectores, y cuáles trae la aplicación de prueba (`enElConjunto`). No protegen nada:
ver `esquema/pila-v1.md`.

**Cobertura** (`cobertura.json`). Los casos del cálculo de ADR-010: con `du`, `e`,
`f` y la fecha del retiro, cuánto cubre lo entregado y cuándo se agota. Los corren
el motor y la web, para que el mesón y el teléfono muestren la misma fecha.

### Generarlos

Los vectores no se editan a mano: salen de `herramientas/generar_vectores.py`, una
implementación de referencia en Python escrita aparte de la de Kotlin, para que un
error compartido no pase inadvertido. Cada caso declara su resultado y el generador
lo comprueba contra el esquema y las reglas antes de escribir.

Con Python 3.12 o 3.13, desde la raíz del repositorio:

```
python -m venv .venv
.venv\Scripts\pip install -r contrato/herramientas/requirements.txt
.venv\Scripts\python contrato/herramientas/generar_vectores.py
```

Con `--comprobar` no escribe nada y falla si algún archivo no está al día. Así
corre en la integración continua (flujo `contrato`).

## Cambios de versión

La clave `v` de la raíz identifica la versión del esquema. La aplicación móvil debe
seguir interpretando códigos de versiones anteriores: un paciente puede cargar hoy
un código impreso hace semanas.
