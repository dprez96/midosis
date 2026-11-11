@echo off
rem Levanta MiDosis en este equipo con datos sinteticos: la base, core y la web de la farmacia.
rem Doble clic, o desde una terminal: infra\local\iniciar.cmd
rem Requiere Docker Desktop abierto, JDK 21, Node 22 y farmacia\.env.local (ver farmacia\README.md).
rem
rem Este archivo va sin tildes a proposito: cmd lee los .cmd por posicion de bytes y una letra
rem acentuada le hace perder el hilo y cortar las lineas.
setlocal
cd /d "%~dp0..\.."

docker info >nul 2>&1
if errorlevel 1 (
  echo Docker Desktop no esta abierto. Abrelo, espera a que diga que esta corriendo y vuelve a intentar.
  pause
  exit /b 1
)
if not exist farmacia\.env.local (
  echo Falta farmacia\.env.local con la configuracion de Firebase. Ver farmacia\README.md.
  pause
  exit /b 1
)

echo Levantando la base de datos...
docker compose -f infra\local\compose.yaml up -d --wait
if errorlevel 1 (
  echo No se pudo levantar la base de datos.
  pause
  exit /b 1
)

echo Abriendo core y la web en dos ventanas. Para detener MiDosis, cierralas.
rem El perfil va en una variable de entorno: en cmd, el "=" de --args=... parte el argumento.
start "MiDosis core" cmd /k "cd core && set SPRING_PROFILES_ACTIVE=local&& .\gradlew.bat bootRun"
start "MiDosis farmacia" cmd /k "cd farmacia && (if not exist node_modules npm install) && npm run dev"

echo Esperando a que core este listo (la primera vez puede tardar varios minutos)...
:esperar
curl -s -f -o nul http://localhost:8080/actuator/health
if errorlevel 1 (
  timeout /t 3 /nobreak >nul
  goto esperar
)
start "" http://localhost:5173
echo Listo: la web esta en http://localhost:5173
timeout /t 5 >nul
endlocal
