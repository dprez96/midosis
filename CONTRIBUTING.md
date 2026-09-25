# Cómo trabajamos

Si es tu primera vez en el proyecto, parte por la [guía de incorporación](docs/incorporacion.md).
Este documento fija las convenciones; aquella explica cómo preparar tu equipo.

## Ramas

Ramas de vida corta sobre `main`. Nada se fusiona sin revisión ni con la integración
continua en rojo.

```
historia/HU-07-emision-del-codigo
defecto/lectura-hid-duplica-caracteres
tarea/actualizar-vectores-de-prueba
```

Una rama que vive más de tres días es señal de que la historia era demasiado grande.

## Mensajes de commit

Imperativo, en español, con la historia entre paréntesis cuando corresponda:

```
Calcula la cobertura del tramo a partir de dosis entregadas (HU-12)
Corrige el descifrado cuando el código llega sin nota del QF
```

## Revisión

Un revisor en general. **Dos revisores obligatorios** en el código criptográfico
(`core/**/cripto/**`, `contrato/**`) y en el motor de planificación, según la
práctica declarada en el informe.

Quien revisa no aprueba lo que no entiende: preguntar es parte de la revisión.

## Antes de abrir el pull request

La plantilla trae la Definición de Terminado como lista de verificación. Seis de
sus condiciones las comprueba la integración continua sola; las otras cuatro
(revisión por par, accesibilidad, visto bueno legal y visto bueno del químico
farmacéutico) las declara quien abre el pull request.

## Datos

Ningún dato real entra al repositorio. Los ejemplos se construyen con el generador
de datos sintéticos, nunca copiando de una pantalla de la farmacia.
