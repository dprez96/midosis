# MiDosis Farmacia

Aplicación web del punto de dispensación. TypeScript con React, construida con Vite.

El producto se reconoce con un lector de código de barras conectado como teclado
(HID): el sistema de la farmacia no se toca ni se integra (ADR-009). El operador
escanea el envase, la aplicación resuelve el producto contra el catálogo, el
químico farmacéutico completa la posología y el comprobante sale impreso con el
código.

## Arranque local

Necesita Node 22, la base local y `core` corriendo (ver `infra/README.md`, sección
«Correr en el equipo»).

1. Copia `.env.example` como `.env.local` y completa los valores de la app web
   «MiDosis Farmacia» desde la consola de Firebase. `.env.local` no se versiona.
2. Instala y levanta:

   ```bash
   npm install
   npm run dev
   ```

3. Abre `http://localhost:5173` e ingresa con un usuario de prueba que tenga comuna y
   rol asignados (`infra/firebase/asignar-perfil.mjs`).

En desarrollo, Vite reenvía `/api` a `core` en `http://localhost:8080`; con
`MIDOSIS_CORE_URL` se apunta a otra instancia.

## Pruebas

```bash
npm test          # Vitest con Testing Library
npm run lint      # ESLint y tipos
npm run build
```

La integración continua corre las tres.

## El lector de código

- El campo del código tiene el foco al abrir el mesón y lo recupera después de cada
  producto, para escanear uno tras otro sin tocar el mouse. Nunca se deshabilita: un
  campo deshabilitado perdería las teclas del siguiente envase.
- El dígito verificador se valida antes de consultar el catálogo: un código mal leído
  pide volver a escanear y no llega a `core`.
- Acepta el GTIN tal cual (8, 12, 13 o 14 dígitos) y la cadena GS1 de un DataMatrix,
  de la que toma el GTIN que sigue al identificador 01.
- Si el código no está en el catálogo, se abre la búsqueda manual por nombre,
  principio activo o comienzo del código. Lo ya capturado en la atención no se pierde.

## La posología

- Cada producto identificado muestra las plantillas de posología frecuente de la
  comuna. Elegir una deja la posología registrada sin escribir nada.
- Si ninguna corresponde, se ingresa en campos estructurados: cantidad y unidad por
  toma, intervalo en horas o días, duración opcional e indicaciones breves.
- Solo el químico farmacéutico ve el botón para guardarla como plantilla comunal; core
  lo vuelve a verificar con el rol del token. El auxiliar puede usarla solo en esa
  atención.
- Después de cada acción el foco vuelve al campo del código. Si igual queda en la página,
  la primera tecla del lector lo devuelve allí.

## Dependencias

La sesión usa `@firebase/app` y `@firebase/auth` directamente, no el paquete
`firebase` completo: ese arrastra Firestore y su cliente gRPC, que la aplicación no
usa y que trae vulnerabilidades ajenas a ella. Las dos se actualizan juntas.

`jsdom` está en la versión 29 porque la 30 exige Node 22.22 o posterior.

## Impresora térmica

El papel térmico se decolora y el ancho útil es limitado, así que los parámetros de
impresión (módulo, corrección de errores, márgenes) están fijados y verificados en
terminal real. No se cambian sin volver a probar sobre papel.
