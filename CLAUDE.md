# MiDosis: instrucciones para Claude Code

MiDosis traslada la indicación posológica desde el mesón de la farmacia popular al
teléfono del paciente, mediante un código impreso en el comprobante, y la convierte
en alarmas ajustadas a su rutina. Se licencia por comuna a municipalidades.

Todo se escribe en **español**, con tildes y eñes correctas: código de dominio,
comentarios, mensajes de commit, pull requests, issues y documentación. Evita el
guion largo (—) en los documentos.

## Arquitectura

| Módulo | Qué es | Tecnología |
|---|---|---|
| `core/` | Servicios centrales: emisión, canje, revocación, catálogo, auditoría | Kotlin, Spring Boot 4, PostgreSQL |
| `motor/` | Planificación y reglas posológicas, compartido por `core/` y `paciente/` | Kotlin puro, bytecode Java 17 |
| `farmacia/` | Aplicación web del punto de dispensación | TypeScript, React |
| `paciente/` | Aplicación móvil | Kotlin, Android nativo |
| `contrato/` | Formato de la credencial y vectores de prueba | CBOR, COSE, Base45 |
| `infra/` | Infraestructura | Google Cloud, `southamerica-west1` |

Las decisiones estructurales están en `docs/adr/`. **Léelas antes de proponer un
cambio de diseño**: muchas alternativas razonables ya fueron evaluadas y
descartadas por un motivo. Si una decisión debe cambiar, se escribe un ADR nuevo
que la reemplaza; el anterior no se edita.

Las que más condicionan el código:

- El código QR es autocontenido y **no lleva ningún identificador del paciente**
  (ADR-001, ADR-002).
- Orden de la pila: firmar, comprimir, cifrar (ADR-004).
- El motor de planificación corre en el teléfono y los datos del tratamiento
  residen en él (ADR-007, ADR-008).
- Duración indicada, dosis entregadas y cobertura son tres valores distintos
  (ADR-010). No los confundas.
- El aislamiento entre comunas se refuerza en PostgreSQL con seguridad a nivel de
  fila, no solo en la consulta (ADR-011). La aplicación se conecta con un usuario
  sin privilegios de dueño: el dueño de la tabla se salta las políticas.

## Reglas de trabajo

- **Nunca subas nada directo a `main`.** Está protegida: todo entra por pull
  request, con una aprobación y los checks `core`, `motor`, `paciente`,
  `farmacia` y `CodeQL` en verde.
- Una rama por historia: `historia/HU-02-lector-de-productos`,
  `tarea/...`, `defecto/...`. Convenciones completas en `CONTRIBUTING.md`.
- Los pull requests se abren desde la cuenta del dueño del repositorio, que no
  puede aprobarlos: la aprobación la da un compañero del equipo.
- **Si un pull request ya tiene aprobaciones, no le subas commits sin avisar
  antes.** Cada commit nuevo descarta las aprobaciones.
- **Antes de acciones que afectan al equipo** (asignar historias, fusionar,
  combinar pull requests, pedir revisiones), si hay algún problema o una
  alternativa distinta a la acordada, preséntala y espera la decisión. No actúes
  y avises después.
- Cada historia cumple la Definición de Terminado de la plantilla de pull request.
  Las reglas clínicas, como la anticipación del aviso de próximo retiro, no se
  cambian sin el visto bueno del químico farmacéutico asesor.

## Datos y secretos

- **Ningún dato real** de pacientes ni de la farmacia, tampoco en pruebas,
  fixtures ni capturas. Solo datos sintéticos.
- **Ninguna credencial** en el repositorio. El escaneo de secretos bloquea el push,
  pero no es excusa para intentarlo.
- Los GTIN de ejemplo deben tener el dígito verificador correcto: el dominio los
  valida.

## Cómo probar

| Módulo | Comando | Requiere |
|---|---|---|
| `core/` | `./gradlew build` | Docker Desktop abierto para las pruebas con PostgreSQL |
| `motor/` | `./gradlew build` | Nada |
| `paciente/` | `./gradlew testDebugUnitTest` | SDK de Android. Las alarmas exactas solo se validan en un teléfono físico, nunca en el emulador |
| `farmacia/` | `npm test` | Node 22 |

La batería de aislamiento entre comunas (pruebas con `Aislamiento` en el nombre)
es obligatoria en cuanto `core/` accede a datos: la integración continua falla si
hay código de persistencia sin esas pruebas.

## Trampas conocidas del entorno Windows

- **Permiso de ejecución de `gradlew`.** Git en Windows no lo registra. Al crear un
  módulo nuevo: `git update-index --add --chmod=+x <modulo>/gradlew`.
- **Python de la Microsoft Store.** Redirige en silencio lo que escribe en
  `AppData\Local` a una carpeta privada. No lo uses para instalar ni extraer
  nada allí: usa PowerShell o `tar`.
- **GitHub CLI** puede no estar en el `PATH` de Bash:
  `export PATH="$PATH:/c/Program Files/GitHub CLI"`.
- **SDK de Android.** `sdkmanager` está obsoleto y parte los nombres de paquete
  en el punto y coma. Usa `android.exe --sdk=<ruta> sdk install "platforms;android-36"`
  desde `cmdline-tools/latest/bin`. Al terminar se cierra con un error aunque el
  paquete quede instalado: verifica en disco.
- **JDK.** Los módulos de Gradle declaran el JDK 21 como toolchain y el resolutor
  Foojay lo descarga si falta. Instalar solo el JRE no basta para compilar.
