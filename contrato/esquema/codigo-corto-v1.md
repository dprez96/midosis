# Código corto y PIN, versión 1

El respaldo del QR (HU-10, tabla 88 del informe): cuando la cámara falla o el papel térmico
se decoloró, el paciente escribe el código corto y el PIN impresos en el comprobante, y la
aplicación descarga el código completo desde core. Lo que descarga es el mismo texto `MD1:`
del QR, que verifica igual que si lo hubiera escaneado ([pila-v1.md](pila-v1.md)).

## Código corto

- **Diez caracteres** de Crockford Base32 (`0123456789ABCDEFGHJKMNPQRSTVWXYZ`, sin I, L, O
  ni U): nueve aleatorios, 45 bits, y uno de verificación.
- Se imprime en dos bloques de cinco: `ABCDE-FGHJK`.
- **Verificador Luhn mod 32**: detecta cualquier carácter mal escrito y casi todas las
  transposiciones de dos vecinos, antes de gastar un intento en el servidor.
- Al leer se ignoran espacios y guiones, se aceptan minúsculas y se corrigen las confusiones
  que prevé Crockford: O por 0, I y L por 1.

La implementación es `CodigoCorto` del módulo `credencial`: la usan core al generarlo y la
aplicación al validarlo antes de enviarlo.

## PIN

Cuatro dígitos al azar, impresos junto al código corto. En la base queda solo derivado con
PBKDF2-HMAC-SHA256 y una sal propia. Con diez mil PINs posibles, lo que de verdad lo protege
es la limitación de intentos.

## Canje

`POST /api/canje` con `{"codigo": "ABCDE-FGHJK", "pin": "1234"}`. Es la única ruta de core sin
token, porque el paciente no tiene cuenta.

| Respuesta | Cuándo |
|---|---|
| 200 `{"codigo": "MD1:..."}` | Código y PIN correctos. El sobre se destruye. |
| 400 | El código no pasa el verificador o el PIN no son cuatro dígitos. No cuenta como intento. |
| 404 | No existe, ya se canjeó o venció: las tres respuestas son iguales. |
| 422 `{"intentosRestantes": n}` | PIN incorrecto. |
| 429 con `Retry-After` | Más de 5 intentos del mismo código en 10 minutos. |
| 423 | 10 PIN fallidos: el código queda bloqueado. Hay que pedir uno nuevo. |

El sobre vive en la tabla `sobre` hasta que se canjea o vence a las 72 horas (RN-03). La
política de la tabla solo deja ver, en el canje, la fila del código que se pide.
