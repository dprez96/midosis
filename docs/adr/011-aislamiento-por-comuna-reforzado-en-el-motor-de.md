# ADR-011. Aislamiento por comuna reforzado en el motor de base de datos

**Estado:** aceptada

## Decisión

Aislamiento por comuna reforzado en el motor de base de datos

## Alternativas descartadas

- Solo condición en la consulta de la aplicación

## Consecuencias

- **Ventaja.** Un error de programación no produce filtración
- **Desventaja.** Mayor complejidad en migraciones y en el agrupamiento de conexiones

---

Registrada en la sección 7 del informe de arquitectura.
