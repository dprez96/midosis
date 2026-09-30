# MiDosis Paciente

Aplicación móvil. Kotlin y Android nativo, desde Android 9.

Nativa y no multiplataforma por decisión explícita (ADR-006): el mayor riesgo
técnico del proyecto está en las alarmas exactas y en el comportamiento de los
administradores de batería de cada fabricante, y ahí conviene el acceso directo a
`AlarmManager` sin una capa intermedia.

## Lo que nunca sale del teléfono

El motor de planificación se ejecuta en el dispositivo (ADR-007) y los datos del
tratamiento residen en él (ADR-008), cifrados con SQLCipher y con la clave en el
almacén de claves de Android. Las preferencias horarias del paciente, que son lo
más revelador de su vida privada, no se transmiten a ningún servidor.

## Banco de dispositivos

Las alarmas se verifican en dispositivos físicos de al menos tres fabricantes
distintos durante 30 días. El emulador no sirve para esto: no reproduce los
administradores de batería propietarios.

## Compilar, instalar y probar

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Requiere el SDK de Android con la plataforma 37 (`ANDROID_HOME` definido) y un teléfono con
la depuración USB activa.

## Alarmas

Se programan con `AlarmManager.setAlarmClock` (ADR-015). Desde Android 14, el permiso de
alarmas exactas lo concede el paciente en Ajustes; la aplicación lo verifica antes de
programar y, si falta, no programa nada en silencio: lo informa.

La aplicación **no declara permiso de Internet**: el sistema le impide usar la red, así que
las alarmas funcionan sin conexión por construcción.

## Registro del banco de dispositivos

Cada disparo queda anotado con su hora programada, su hora real y el desfase, sin ningún
dato del tratamiento. Para extraerlo de un teléfono de pruebas:

```bash
adb shell run-as cl.midosis.paciente cat files/disparos.csv
```

