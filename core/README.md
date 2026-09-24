# MiDosis Core

Servicios centrales: emisión, canje, revocación y consulta de estado del código de
tratamiento; catálogo de productos; motor de reglas; auditoría y privacidad.

Kotlin sobre Spring Boot, arquitectura hexagonal. La lógica de dominio (posología,
cobertura del tramo, validación de coherencia) no conoce el framework ni la base de
datos: se prueba sin levantar el contexto de Spring.

## Arranque local

```bash
docker compose up -d postgres
./gradlew bootRun --args='--spring.profiles.active=local'
```

## Aislamiento entre comunas

Reforzado en el motor de base de datos, no solo en la consulta de la aplicación
(ADR-011). La batería de pruebas de aislamiento es obligatoria en cada compilación:
un error de programación no debe poder producir una filtración entre comunas.

## Claves

Las claves de firma viven en Cloud KMS. En desarrollo se usa un par de prueba
generado localmente, que nunca emite códigos destinados a un paciente real.
