@echo off
setlocal EnableExtensions
chcp 65001 >nul
title Smart Parking - DEMO (giu cua so nay mo khi dang demo)

REM Luon chay tu thu muc chua file .bat — khong phu thuoc o dia hay duong dan clone project.
set "PROJECT_ROOT=%~dp0"
if "%PROJECT_ROOT:~-1%"=="\" set "PROJECT_ROOT=%PROJECT_ROOT:~0,-1%"
cd /d "%PROJECT_ROOT%"

set "DEMO_DIR=%PROJECT_ROOT%\frontend\demo"
set "VENV_PY=%DEMO_DIR%\.venv-demo\Scripts\python.exe"

echo ============================================================
echo    SMART PARKING - KHOI DONG DEMO
echo ============================================================
echo.
echo    Thu muc project: %PROJECT_ROOT%
echo.

echo [1/5] Kiem tra Docker...
docker info >nul 2>&1
if errorlevel 1 goto :err_docker

echo [2/5] Kiem tra file .env...
if not exist "%PROJECT_ROOT%\.env" goto :err_env

echo [3/5] Kiem tra / tao Python venv demo (nhe, chi fastapi+uvicorn+httpx)...
if not exist "%VENV_PY%" (
  echo    Chua co .venv-demo — dang tao lan dau, doi vai phut...
  where python >nul 2>&1
  if errorlevel 1 goto :err_python
  pushd "%DEMO_DIR%"
  python -m venv .venv-demo
  if errorlevel 1 goto :err_venv_create
  call .venv-demo\Scripts\pip.exe install -q -r requirements-demo.txt
  if errorlevel 1 goto :err_venv_pip
  popd
)
if not exist "%VENV_PY%" goto :err_venv_missing

echo [4/5] Khoi dong cac dich vu Docker...
docker compose -f "%PROJECT_ROOT%\docker-compose.yml" up -d kafka kafka-ui admin-db parking-db billing-db minio
if errorlevel 1 goto :err_compose
docker compose -f "%PROJECT_ROOT%\docker-compose.yml" up -d admin-service parking-service billing-service edge-agent
if errorlevel 1 goto :err_compose

echo    Cho cac dich vu san sang 25 giay...
timeout /t 25 /nobreak >nul

echo [5/5] Chuan bi HTTPS va khoi dong trang demo...
"%VENV_PY%" "%DEMO_DIR%\setup_cert.py"
set "CERT_EXIT=%ERRORLEVEL%"
if "%CERT_EXIT%"=="10" (
  echo    IP LAN thay doi — tao lai parking-service...
  docker compose -f "%PROJECT_ROOT%\docker-compose.yml" up -d parking-service
)

REM Doc EDGE_API_KEY va mat khau admin tu .env de proxy demo khop backend.
for /f "usebackq tokens=1,* delims==" %%A in (`findstr /b /i "EDGE_API_KEY= ADMIN_SEED_PASSWORD=" "%PROJECT_ROOT%\.env"`) do (
  if /i "%%A"=="EDGE_API_KEY" set "EDGE_API_KEY=%%B"
  if /i "%%A"=="ADMIN_SEED_PASSWORD" set "ADMIN_PASS=%%B"
)
if not defined EDGE_API_KEY set "EDGE_API_KEY=dev-edge-key"
if not defined ADMIN_PASS set "ADMIN_PASS=ChangeMe123!"

echo.
echo    Giu cua so DEN nay mo trong luc demo. Dong cua so = tat trang demo.
echo    Laptop:  https://localhost:8095
echo.

set "EDGE_URL=http://localhost:8000"
set "PARKING_URL=http://localhost:8081"
set "BILLING_URL=http://localhost:8082"
set "ADMIN_URL=http://localhost:8083"
set "ADMIN_USER=admin"

"%VENV_PY%" -m uvicorn server:app --app-dir "%DEMO_DIR%" --host 0.0.0.0 --port 8095 ^
  --ssl-keyfile "%DEMO_DIR%\certs\key.pem" --ssl-certfile "%DEMO_DIR%\certs\cert.pem"

echo.
echo Trang demo da dung.
pause
exit /b 0

:err_docker
echo.
echo    Docker chua chay!
echo    Mo Docker Desktop, doi bieu tuong con ca mau XANH, roi chay lai file nay.
echo.
pause
exit /b 1

:err_env
echo.
echo    Khong tim thay file .env trong:
echo    %PROJECT_ROOT%
echo.
echo    Chay: copy .env.example .env
echo    roi dien gia tri thuc truoc khi demo.
echo.
pause
exit /b 1

:err_python
echo.
echo    Khong tim thay lenh "python" tren PATH.
echo    Cai Python 3.11+ hoac them python vao PATH.
echo.
pause
exit /b 1

:err_venv_create
echo.
echo    Tao venv that bai tai: %DEMO_DIR%\.venv-demo
echo.
popd 2>nul
pause
exit /b 1

:err_venv_pip
echo.
echo    Cai pip packages that bai. Thu chay thu cong:
echo    cd %DEMO_DIR%
echo    python -m venv .venv-demo
echo    .venv-demo\Scripts\pip install -r requirements-demo.txt
echo.
popd 2>nul
pause
exit /b 1

:err_venv_missing
echo.
echo    Khong tim thay: %VENV_PY%
echo.
pause
exit /b 1

:err_compose
echo.
echo    docker compose that bai. Kiem tra Docker va file:
echo    %PROJECT_ROOT%\docker-compose.yml
echo.
popd 2>nul
pause
exit /b 1
