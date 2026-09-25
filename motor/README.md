# MiDosis Motor

Biblioteca compartida con la lógica de planificación y de reglas posológicas.
Kotlin puro: no depende de Android, de Spring ni de una base de datos, y se prueba
sin levantar nada.

La usan dos módulos, por razones distintas:

| Módulo | Para qué la usa |
|---|---|
| `paciente/` | Traducir la posología a un calendario de tomas en el teléfono. El motor se ejecuta en el dispositivo (ADR-007), así las preferencias horarias del paciente nunca salen de él |
| `core/` | Validar la coherencia posológica y calcular la cobertura del tramo al emitir el código |

Tener una sola implementación garantiza que el mesón y el teléfono calculen lo
mismo. Si la cobertura se calculara dos veces, en dos lenguajes, tarde o temprano
darían resultados distintos.

## Compatibilidad

Se construye con el JDK 21, pero genera bytecode de Java 17, que es lo que acepta
la aplicación Android. No uses API de Java posteriores a la 17.

## Pruebas

```bash
./gradlew test
```

Es el módulo donde más importa la batería de pruebas: un error aquí se traduce en
una alarma a la hora equivocada. Toda regla nueva llega con sus casos límite
probados, y los cambios requieren dos revisores.
