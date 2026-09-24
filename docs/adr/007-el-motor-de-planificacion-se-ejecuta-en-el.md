# ADR-007. El motor de planificación se ejecuta en el cliente

**Estado:** aceptada

## Decisión

El motor de planificación se ejecuta en el cliente

## Alternativas descartadas

- Cálculo del calendario en el servidor

## Consecuencias

- **Ventaja.** Funciona sin conexión; las preferencias horarias nunca salen del dispositivo
- **Desventaja.** Lógica duplicada para validación a mitigado con biblioteca compartida y pruebas exhaustivas

---

Registrada en la sección 7 del informe de arquitectura.
