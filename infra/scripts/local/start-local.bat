@echo off
setlocal EnableExtensions

for %%I in ("%~dp0..\..\..") do set "ROOT_DIR=%%~fI"
set "COMPOSE_FILE=%ROOT_DIR%\infra\docker\docker-compose.local.yml"
set "ENV_FILE=%ROOT_DIR%\.env"
set "ENV_EXAMPLE_FILE=%ROOT_DIR%\.env.example"
set "KEY_DIR=%ROOT_DIR%\infra\keys"
set "PRIVATE_KEY_FILE=%KEY_DIR%\private.pem"
set "PUBLIC_KEY_FILE=%KEY_DIR%\public.pem"
set "SKIP_BUILD=false"
set "FRONTEND_ONLY=false"

:parse_args
if "%~1"=="" goto args_done
if /I "%~1"=="--skip-build" (
  set "SKIP_BUILD=true"
  shift
  goto parse_args
)
if /I "%~1"=="-s" (
  set "SKIP_BUILD=true"
  shift
  goto parse_args
)
if /I "%~1"=="--frontend-only" (
  set "FRONTEND_ONLY=true"
  shift
  goto parse_args
)
if /I "%~1"=="-f" (
  set "FRONTEND_ONLY=true"
  shift
  goto parse_args
)
if /I "%~1"=="--help" goto usage
if /I "%~1"=="-h" goto usage
echo Unknown argument: %~1
goto usage_error

:args_done
call :ensure_env || exit /b 1
call :sync_env_from_example || exit /b 1
call :load_env || exit /b 1
call :apply_defaults
call :ensure_keys || exit /b 1
if /I "%FRONTEND_ONLY%"=="true" (
  call :start_frontend_only || exit /b 1
  exit /b 0
)
call :build_jars || exit /b 1
call :start_stack || exit /b 1
exit /b 0

:usage
echo Usage: infra\scripts\local\start-local.bat [--skip-build ^| -s] [--frontend-only ^| -f]
echo.
echo Starts the full local Studium stack:
echo   1. Ensures .env exists
echo   2. Ensures JWT RSA keys exist
echo   3. Builds all bootable services unless --skip-build is used
echo   4. Starts Docker Compose infrastructure, backend services, and the frontend container
echo   5. Runs demo seed when DEMO_SEED_ENABLED=true and bash is available
echo.
echo Frontend-only refresh:
echo   --frontend-only ^| -f
echo     Rebuilds and restarts only the frontend container without restarting the rest of the stack.
exit /b 0

:usage_error
call :usage
exit /b 1

:ensure_env
if exist "%ENV_FILE%" (
  echo .env already exists
  exit /b 0
)
if exist "%ENV_EXAMPLE_FILE%" (
  copy /Y "%ENV_EXAMPLE_FILE%" "%ENV_FILE%" >nul
  echo .env created from .env.example
  exit /b 0
)
call :create_minimal_env || exit /b 1
echo .env.example missing, created minimal .env
exit /b 0

:sync_env_from_example
if not exist "%ENV_EXAMPLE_FILE%" exit /b 0
set "APPENDED_KEYS="
for /f "usebackq eol=# tokens=1* delims==" %%A in ("%ENV_EXAMPLE_FILE%") do call :append_env_key_if_missing "%%A" "%%A=%%B"
if defined APPENDED_KEYS echo Appended missing .env keys from .env.example:%APPENDED_KEYS%
exit /b 0

:append_env_key_if_missing
set "ENV_KEY=%~1"
set "ENV_LINE=%~2"
if "%ENV_KEY%"=="" exit /b 0
findstr /b /c:"%ENV_KEY%=" "%ENV_FILE%" >nul 2>&1
if not errorlevel 1 exit /b 0
>>"%ENV_FILE%" echo(
>>"%ENV_FILE%" echo(%ENV_LINE%
set "APPENDED_KEYS=%APPENDED_KEYS% %ENV_KEY%"
exit /b 0

:create_minimal_env
(
  echo # Generated minimal local defaults
  echo GATEWAY_PORT=8080
  echo AUTH_PORT=8081
  echo PROFILE_PORT=8082
  echo FILE_PORT=8083
  echo NOTIFICATION_PORT=8084
  echo EDUCATION_PORT=8085
  echo ASSIGNMENT_PORT=8086
  echo TESTING_PORT=8087
  echo SCHEDULE_PORT=8088
  echo ANALYTICS_PORT=8089
  echo AUDIT_PORT=8090
  echo POSTGRES_DB=postgres
  echo POSTGRES_USER=postgres
  echo POSTGRES_PASSWORD=postgres
  echo AUTH_DB_SCHEMA=auth
  echo PROFILE_DB_SCHEMA=profile
  echo FILE_DB_SCHEMA=file
  echo NOTIFICATION_DB_SCHEMA=notification
  echo EDUCATION_DB_SCHEMA=education
  echo ASSIGNMENT_DB_SCHEMA=assignment
  echo TESTING_DB_SCHEMA=testing
  echo SCHEDULE_DB_SCHEMA=schedule
  echo ANALYTICS_DB_SCHEMA=analytics
  echo AUDIT_DB_SCHEMA=audit
  echo KAFKA_PORT=29092
  echo KAFKA_BOOTSTRAP_SERVERS=localhost:29092
  echo KAFKA_CLUSTER_ID=MkU3OEVBNTcwNTJENDM2Qk
  echo MINIO_PORT=9000
  echo MINIO_CONSOLE_PORT=9001
  echo MINIO_ROOT_USER=minio
  echo MINIO_ROOT_PASSWORD=minio123
  echo MINIO_BUCKET_PUBLIC=public
  echo MINIO_BUCKET_PRIVATE=private
  echo FRONTEND_PORT=3000
  echo FRONTEND_VITE_API_BASE_URL=http://localhost:8080
  echo JWT_ISSUER=dev.knalis.auth-service
  echo JWT_AUDIENCE=dev.knalis.api
  echo FILE_INTERNAL_SHARED_SECRET=change-me-file-internal
  echo NOTIFICATION_INTERNAL_SHARED_SECRET=change-me-notification-internal
  echo EDUCATION_INTERNAL_SHARED_SECRET=change-me-education-internal
  echo AUDIT_INTERNAL_SHARED_SECRET=change-me-audit-internal
  echo AUTH_OWNER_SEED_ENABLED=true
  echo AUTH_OWNER_USERNAME=owner
  echo AUTH_OWNER_EMAIL=owner@example.com
  echo AUTH_OWNER_PASSWORD=ChangeMe123^!
  echo AUTH_MFA_ENABLED=true
  echo AUTH_MFA_ENCRYPTION_KEY=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=
  echo DEMO_SEED_ENABLED=true
)>"%ENV_FILE%"
exit /b 0

:load_env
for /f "usebackq eol=# tokens=1* delims==" %%A in ("%ENV_FILE%") do if not "%%A"=="" set "%%A=%%B"
exit /b 0

:apply_defaults
if not defined AUTH_REPLICAS set "AUTH_REPLICAS=1"
if not defined PROFILE_REPLICAS set "PROFILE_REPLICAS=1"
if not defined EDUCATION_REPLICAS set "EDUCATION_REPLICAS=1"
if not defined SCHEDULE_REPLICAS set "SCHEDULE_REPLICAS=1"
if not defined ASSIGNMENT_REPLICAS set "ASSIGNMENT_REPLICAS=1"
if not defined TESTING_REPLICAS set "TESTING_REPLICAS=1"
if not defined FILE_REPLICAS set "FILE_REPLICAS=1"
if not defined ANALYTICS_REPLICAS set "ANALYTICS_REPLICAS=1"
if not defined AUDIT_REPLICAS set "AUDIT_REPLICAS=1"
if not defined NOTIFICATION_REPLICAS set "NOTIFICATION_REPLICAS=1"
if not defined GATEWAY_REPLICAS set "GATEWAY_REPLICAS=1"
exit /b 0

:require_openssl
where openssl >nul 2>&1
if errorlevel 1 (
  echo openssl is required to generate RSA keys. Install openssl and rerun the script.
  exit /b 1
)
exit /b 0

:ensure_keys
if not exist "%KEY_DIR%" mkdir "%KEY_DIR%"

if exist "%PRIVATE_KEY_FILE%" if exist "%PUBLIC_KEY_FILE%" (
  echo JWT RSA keys already exist
  exit /b 0
)

if exist "%PRIVATE_KEY_FILE%" if not exist "%PUBLIC_KEY_FILE%" (
  call :require_openssl || exit /b 1
  openssl rsa -in "%PRIVATE_KEY_FILE%" -pubout -out "%PUBLIC_KEY_FILE%" >nul 2>&1
  if errorlevel 1 exit /b 1
  echo Generated public.pem from existing private.pem
  exit /b 0
)

if not exist "%PRIVATE_KEY_FILE%" if exist "%PUBLIC_KEY_FILE%" (
  echo infra\keys\public.pem exists but infra\keys\private.pem is missing. Restore the private key or remove the orphaned public key and rerun.
  exit /b 1
)

call :require_openssl || exit /b 1
openssl genrsa -out "%PRIVATE_KEY_FILE%" 2048 >nul 2>&1
if errorlevel 1 exit /b 1
openssl rsa -in "%PRIVATE_KEY_FILE%" -pubout -out "%PUBLIC_KEY_FILE%" >nul 2>&1
if errorlevel 1 exit /b 1
echo Generated JWT RSA key pair in infra\keys
exit /b 0

:build_jars
if /I "%SKIP_BUILD%"=="true" (
  echo Skipping Gradle build
  exit /b 0
)

echo Building boot JARs for all application services...
pushd "%ROOT_DIR%" >nul
call gradlew.bat ^
  :apps:auth-service:bootJar ^
  :apps:profile-service:bootJar ^
  :apps:education-service:bootJar ^
  :apps:schedule-service:bootJar ^
  :apps:assignment-service:bootJar ^
  :apps:testing-service:bootJar ^
  :apps:file-service:bootJar ^
  :apps:analytics-service:bootJar ^
  :apps:audit-service:bootJar ^
  :apps:notification-service:bootJar ^
  :apps:gateway:bootJar
set "BUILD_EXIT=%ERRORLEVEL%"
popd >nul
exit /b %BUILD_EXIT%

:start_stack
echo Starting infrastructure containers...
docker compose --env-file "%ENV_FILE%" -f "%COMPOSE_FILE%" up -d postgres redis kafka minio || exit /b 1

echo Initializing PostgreSQL schemas...
docker compose --env-file "%ENV_FILE%" -f "%COMPOSE_FILE%" run --rm db-init || exit /b 1

echo Initializing Kafka topics...
docker compose --env-file "%ENV_FILE%" -f "%COMPOSE_FILE%" run --rm kafka-init || exit /b 1

echo Initializing MinIO buckets...
docker compose --env-file "%ENV_FILE%" -f "%COMPOSE_FILE%" run --rm minio-init || exit /b 1

echo Starting application containers...
docker compose --env-file "%ENV_FILE%" -f "%COMPOSE_FILE%" up -d --build ^
  --scale auth-service=%AUTH_REPLICAS% ^
  --scale profile-service=%PROFILE_REPLICAS% ^
  --scale education-service=%EDUCATION_REPLICAS% ^
  --scale schedule-service=%SCHEDULE_REPLICAS% ^
  --scale assignment-service=%ASSIGNMENT_REPLICAS% ^
  --scale testing-service=%TESTING_REPLICAS% ^
  --scale file-service=%FILE_REPLICAS% ^
  --scale analytics-service=%ANALYTICS_REPLICAS% ^
  --scale audit-service=%AUDIT_REPLICAS% ^
  --scale notification-service=%NOTIFICATION_REPLICAS% ^
  --scale gateway=%GATEWAY_REPLICAS% ^
  auth-service profile-service education-service schedule-service assignment-service testing-service file-service analytics-service audit-service notification-service gateway frontend || exit /b 1

call :seed_demo_data || exit /b 1
echo Local stack started successfully
exit /b 0

:start_frontend_only
echo Rebuilding and starting only the frontend container...
docker compose --env-file "%ENV_FILE%" -f "%COMPOSE_FILE%" up -d --build --no-deps frontend || exit /b 1
echo Frontend container started successfully
exit /b 0

:seed_demo_data
if /I "%DEMO_SEED_ENABLED%"=="false" (
  echo Demo seed disabled via DEMO_SEED_ENABLED
  exit /b 0
)

where bash >nul 2>&1
if errorlevel 1 (
  echo Demo seed requires bash on Windows. Stack startup completed, but automatic seed was skipped.
  exit /b 0
)

echo Running demo seed...
bash "%ROOT_DIR%\infra\scripts\local\seed-demo.sh"
exit /b %ERRORLEVEL%
