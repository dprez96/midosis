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
  -> texto        Base45
  -> impresión    QR, ISO/IEC 18004
```

Se firma antes de cifrar para que el emisor no quede a la vista de un lector
genérico, y se comprime antes de cifrar porque después del cifrado no hay
redundancia que comprimir.

## Carga útil

| Archivo | Qué es |
|---|---|
| `esquema/carga-v1.cddl` | La estructura, en CDDL (RFC 8610). Es normativa. |
| `esquema/carga-v1.md` | Qué significa cada clave y las reglas que CDDL no puede expresar. |

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

Hay dos clases de archivos:

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
- La HU-09 agrega a estos archivos las capas siguientes de la pila (firma,
  compresión, cifrado y Base45) y los casos que solo existen en ellas, como
  `invalido-firma-alterada`.

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
