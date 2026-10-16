# ADR-017. La aplicación usa Internet solo para el canje con código corto

**Estado:** propuesta

## Contexto

Hasta S2 la aplicación del paciente no declaraba el permiso de Internet, a propósito: que las
alarmas y la verificación funcionen sin conexión no dependía de una promesa del código, sino
del sistema operativo. El manifiesto anunciaba que el permiso llegaría «con su revisión».

La HU-10 lo vuelve necesario: cuando el QR no se puede leer, el código corto y el PIN se
canjean en core (modo B del informe), y eso es inevitablemente en línea.

## Decisión

La aplicación declara `INTERNET` y lo usa únicamente en el canje con código corto, cuando el
paciente lo pide. La configuración de red exige HTTPS; la compilación de depuración admite
HTTP solo hacia `127.0.0.1`, para probar contra core en el computador con `adb reverse`.

## Alternativas descartadas

- Canjear el código corto sin red: imposible, el código corto no contiene el tratamiento.
- Postergar la HU-10 hasta tener sincronización: deja sin respaldo al paciente cuyo papel
  térmico se decoloró.

## Consecuencias

- **Ventaja.** El paciente puede cargar el tratamiento aunque el QR no se lea.
- **Desventaja.** El sistema ya no impide que la aplicación use la red: la garantía pasa a ser
  la revisión del código. Cualquier uso nuevo de la red requiere un ADR propio.
- Las alarmas, la verificación del QR (ADR-013) y los datos del tratamiento (ADR-008) siguen
  sin pasar por la red. Solo viajan el código corto y el PIN impresos.
