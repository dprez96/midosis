# Incorporación al proyecto

Guía para empezar a trabajar en MiDosis desde cero. Los pasos 1 a 4 se hacen una
sola vez por computador.

## 1. Acceso al repositorio

El repositorio es privado. Cuando el dueño te invite, llega un correo de GitHub con
el asunto *"dprez96 invited you to dprez96/midosis"*. Acéptala desde ese correo o
entrando a **github.com/dprez96/midosis/invitations** y pulsando **Accept
invitation**. La invitación vence a los siete días.

## 2. Herramientas base

Instala Git y GitHub CLI desde PowerShell:

```bash
winget install Git.Git
```

```bash
winget install GitHub.cli
```

Cierra la terminal y ábrela de nuevo, para que reconozca los programas recién
instalados. Después conéctate a tu cuenta, eligiendo **GitHub.com → HTTPS → Login
with a web browser**:

```bash
gh auth login
```

Configura tu identidad con el mismo correo de tu cuenta de GitHub, para que tus
commits queden a tu nombre:

```bash
git config --global user.name "Tu Nombre Apellido"
```

```bash
git config --global user.email "tu-correo@ejemplo.com"
```

## 3. Herramientas según tu frente

Cada una se instala con `winget install <identificador>`.

| Frente | Carpeta | Qué instalar |
|---|---|---|
| Backend | `core/` | `EclipseAdoptium.Temurin.21.JDK`, `JetBrains.IntelliJIDEA.Community`, `Docker.DockerDesktop` |
| Web | `farmacia/` | `OpenJS.NodeJS.22`, `Microsoft.VisualStudioCode` |
| Móvil | `paciente/` | `Google.AndroidStudio`, que trae su propio JDK y el SDK de Android |
| Arquitectura y seguridad | `contrato/`, `infra/` | `EclipseAdoptium.Temurin.21.JDK`, `JetBrains.IntelliJIDEA.Community`, `Docker.DockerDesktop` |

Dos detalles que evitan problemas:

- **Node en versión 22**, no la LTS (hoy la 24). La integración continua corre con
  la 22, y conviene que lo que funciona en tu equipo funcione igual allá.
- **JDK, no JRE.** Si Java ya venía instalado en tu computador, lo más probable es
  que sea solo el JRE, que ejecuta programas pero no los compila. Instala el JDK de
  la tabla aunque `java -version` responda.

## 4. Descargar el proyecto

```bash
gh repo clone dprez96/midosis
```

Si el módulo en que vas a trabajar ya tiene código, comprueba que compile en tu
equipo antes de tocar nada. Para `core/`, desde esa carpeta:

```bash
./gradlew test
```

En PowerShell es `.\gradlew.bat test`. La primera vez tarda varios minutos, porque
descarga Gradle y el JDK que el proyecto necesita; las siguientes son rápidas. Tiene
que terminar en `BUILD SUCCESSFUL`.

## 5. El ciclo de una historia

Las historias están en **Issues**, agrupadas por sprint en
[Milestones](https://github.com/dprez96/midosis/milestones). Cada una trae su
narrativa, sus criterios de aceptación y los requerimientos que implementa.

1. **Tómala.** Ábrela en GitHub y asígnate en *Assignees*, para que nadie más la
   tome en paralelo.
2. **Actualiza `main`.** `git switch main` y después `git pull`.
3. **Crea tu rama.** `git switch -c historia/HU-02-lector-de-productos`
4. **Trabaja y guarda.** `git add .` y
   `git commit -m "Identifica el producto por su GTIN (HU-02)"`
5. **Súbela.** `git push -u origin historia/HU-02-lector-de-productos`
6. **Abre el *pull request*.** `gh pr create`. Completa la plantilla y escribe
   `Closes #7` en la descripción, con el número de tu historia: así se cierra sola
   al fusionar.
7. **Espera** la revisión de un compañero y que todas las verificaciones estén en
   verde. Recién ahí se fusiona.

Las convenciones de nombres de ramas y de mensajes están en
[CONTRIBUTING.md](../CONTRIBUTING.md).

## 6. Revisar el trabajo de otro

En **Pull requests**, abre el que te toque y ve a la pestaña **Files changed**. Lee
el código y deja comentarios en las líneas que no entiendas. Para terminar, usa
**Review changes**: *Approve* si está bien, o *Request changes* explicando qué hay
que corregir.

Revisar no es un trámite. Si no entiendes algo, pregunta: eso también es parte de
la revisión, y lo que tú no entiendes probablemente tampoco lo entienda quien
mantenga ese código en seis meses.

## 7. Cuatro reglas

- **Nunca subir directo a `main`.** Todo pasa por un *pull request*, sin excepción.
- **Ningún dato real** de pacientes ni de la farmacia, ni siquiera en pruebas o
  capturas de pantalla. Los ejemplos se construyen con datos sintéticos.
- **Ninguna contraseña ni clave** en el repositorio. Si alguna se sube por error,
  avisa para cambiarla: borrar el commit no basta, porque el valor ya quedó en el
  historial.
- **Ramas cortas.** Si una rama lleva más de tres días abierta, la historia era
  demasiado grande y conviene partirla.

## Si algo falla

| Síntoma | Qué hacer |
|---|---|
| `gh` o `git` no se reconocen como comandos | Cierra y abre la terminal. Si sigue, reinicia sesión en Windows |
| `Cannot find a Java installation ... matching languageVersion=21` | Tienes el JRE y no el JDK. Instala `EclipseAdoptium.Temurin.21.JDK` |
| `Cannot connect to the Docker daemon` o `dockerDesktopLinuxEngine` | Docker Desktop está instalado pero cerrado. Ábrelo desde el menú de inicio |
| `./gradlew` tarda mucho la primera vez | Es normal: descarga Gradle y el JDK. Solo ocurre una vez |
| GitHub rechaza tu `push` con *non-fast-forward* | Alguien subió cambios antes. `git pull --rebase` y vuelve a subir |
| Una verificación sale en rojo en tu *pull request* | Pulsa *Details* junto a ella: el registro dice qué falló. No se fusiona hasta que esté en verde |
