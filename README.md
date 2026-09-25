# MiDosis

MiDosis traslada la indicación posológica desde el mesón de la farmacia al teléfono
del paciente mediante un código impreso en el comprobante de entrega, y convierte
esa indicación en recordatorios ajustados a la rutina real de la persona.

El código es autocontenido: no es un enlace ni un identificador de consulta. Se lee
y se verifica sin conexión, y no contiene ningún dato que identifique al paciente.

## Módulos

| Carpeta | Qué es | Tecnología |
|---|---|---|
| `core/` | Servicios centrales, motor de reglas, emisión y canje | Kotlin, Spring Boot |
| `farmacia/` | Aplicación web del punto de dispensación | TypeScript, React |
| `paciente/` | Aplicación móvil | Kotlin, Android 9+ |
| `contrato/` | Esquema de la credencial y vectores de prueba | CBOR, COSE, Base45 |
| `infra/` | Infraestructura como código | Google Cloud, `southamerica-west1` |
| `docs/` | Decisiones de arquitectura y trazabilidad | Markdown |

## El contrato es el centro

`contrato/` contiene el esquema de la carga útil y un juego de vectores de prueba:
códigos de ejemplo con el resultado que cada módulo debe producir al procesarlos.
Los tres módulos los consumen en su propia batería de pruebas, de modo que un cambio
de formato rompe la compilación de los tres y no pasa inadvertido.

## Puesta en marcha

**¿Recién llegas al proyecto?** Empieza por la [guía de incorporación](docs/incorporacion.md):
qué instalar según tu frente, cómo se trabaja cada historia y qué hacer si algo falla.

Requisitos: JDK 21, Node 22, Android Studio y Docker para la base de datos local.

```bash
git clone <url-del-repositorio>
cd midosis
```

Cada módulo documenta su arranque en su propio `README.md`.

## Ambientes

Desarrollo y QA operan exclusivamente con datos sintéticos. Producción es el único
ambiente con datos reales y controles completos.

## Confidencialidad

Repositorio privado. Ningún dato real de pacientes, de la farmacia o del piloto
puede incorporarse al repositorio, ni siquiera en pruebas o capturas de pantalla.
