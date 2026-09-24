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

## Cambios de versión

La clave `v` de la raíz identifica la versión del esquema. La aplicación móvil debe
seguir interpretando códigos de versiones anteriores: un paciente puede cargar hoy
un código impreso hace semanas.
