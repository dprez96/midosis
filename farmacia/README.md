# MiDosis Farmacia

Aplicación web del punto de dispensación. TypeScript con React.

El producto se reconoce con un lector de código de barras conectado como teclado
(HID): el sistema de la farmacia no se toca ni se integra (ADR-009). El operador
escanea el envase, la aplicación resuelve el producto contra el catálogo, el
químico farmacéutico completa la posología y el comprobante sale impreso con el
código.

## Arranque local

```bash
npm install
npm run dev
```

## Impresora térmica

El papel térmico se decolora y el ancho útil es limitado, así que los parámetros de
impresión (módulo, corrección de errores, márgenes) están fijados y verificados en
terminal real. No se cambian sin volver a probar sobre papel.
