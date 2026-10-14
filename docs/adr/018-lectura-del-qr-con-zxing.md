# ADR-018. El QR se lee con ZXing y CameraX, no con ML Kit

**Estado:** propuesta

## Contexto

La HU-11 necesita leer el QR del comprobante con la cámara. En Android las opciones habituales
son ML Kit de Google y ZXing, de código abierto.

## Decisión

La aplicación lee el QR con ZXing (`com.google.zxing:core`) sobre los cuadros de CameraX. La
imagen se analiza en el teléfono y no se guarda.

## Alternativas descartadas

- ML Kit: lee algo mejor en condiciones difíciles, pero envía datos de uso a Google, y una
  aplicación de salud que no pide identidad no debe abrir ese canal (ADR-002, ADR-008). Su
  versión sin modelo incluido depende además de los servicios de Google Play.

## Consecuencias

- **Ventaja.** Ningún tercero recibe datos de la lectura, y funciona en teléfonos sin servicios
  de Google.
- **Desventaja.** ZXing está en mantenimiento, sin funciones nuevas. Si en el piloto la lectura
  falla en papel térmico gastado, el respaldo es el código corto (HU-10) antes que cambiar de
  biblioteca.
- **Desventaja.** El análisis por cuadro consume algo más de batería mientras la cámara está
  abierta; solo ocurre al cargar un tratamiento.

Probada el 14/10/2026 en un Galaxy S24, leyendo un QR de 532 caracteres desde la pantalla de un
computador.
