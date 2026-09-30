# ADR-015. Las alarmas de toma se programan con setAlarmClock

**Estado:** aceptada

## Contexto

El informe indica programar las alarmas con `AlarmManager.setExactAndAllowWhileIdle`. El
criterio de aceptación de la HU-15 exige que al menos el 99 % de los disparos ocurra dentro
de 5 minutos de la hora programada, en un banco de 30 días sobre teléfonos de al menos tres
fabricantes.

En el modo de ahorro profundo (Doze), Android limita la frecuencia con que entrega las
alarmas de `setExactAndAllowWhileIdle`: no las despacha más seguido que cada cierto
intervalo, que en los estados de mayor ahorro puede llegar a unos 15 minutos. Un paciente
con dos medicamentos a pocos minutos de distancia, de noche y con el teléfono quieto, puede
recibir la segunda alarma tarde. La agrupación de la HU-18 solo resuelve las tomas del mismo
minuto.

## Decisión

Las alarmas de toma se programan con `AlarmManager.setAlarmClock`.

## Alternativas descartadas

- `setExactAndAllowWhileIdle`, la del informe, por la limitación de frecuencia descrita.
- `WorkManager`, que no garantiza la hora: agrupa ejecuciones para ahorrar batería.
- Un servicio en primer plano permanente, que consume batería, muestra una notificación
  fija y los fabricantes igual lo terminan.

## Consecuencias

- **Ventaja.** Es el mecanismo de los despertadores: el sistema sale del ahorro profundo
  para cumplirlas y no aplica límite de frecuencia. Es el camino con más probabilidad de
  cumplir el 99 % dentro de 5 minutos.
- **Ventaja.** Usa el mismo permiso que la alternativa, `SCHEDULE_EXACT_ALARM`, que en
  Android 14 y posteriores concede el paciente en Ajustes.
- **Desventaja.** El teléfono muestra el ícono de alarma y la hora de la próxima en la
  barra de estado y en la pantalla de bloqueo. No revela qué medicamento es, pero sí que hay
  una alarma. Para la mayoría de los pacientes es incluso útil; se evaluará en el piloto.
- **Desventaja.** Si el paciente tiene además un despertador, la barra muestra la alarma que
  ocurra primero, sea de MiDosis o no.

La medición del banco de dispositivos confirmará o refutará esta decisión: el registro de
disparos de la aplicación guarda el desfase de cada alarma.

---

Se aparta del informe, que menciona `setExactAndAllowWhileIdle`. Al aceptarse, el informe
se actualiza para indicar `setAlarmClock`.
