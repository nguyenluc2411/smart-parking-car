@echo off
chcp 65001 >nul
title Smart Parking - DEMO (giu cua so nay mo khi dang demo)
cd /d "D:\SU26\MSS301\Group_Project\smart-parking-car"

echo ============================================================
echo    SMART PARKING - KHOI DONG DEMO
echo ============================================================
echo.

echo [1/4] Kiem tra Docker...
docker info >nul 2>&1
if errorlevel 1 (
  echo.
  echo    Docker chua chay!
  echo    -^> Mo ung dung "Docker Desktop", doi bieu tuong con ca voi chuyen mau XANH,
  echo       roi chay lai file nay.
  echo.
  pause
  exit /b 1
)

echo [2/4] Khoi dong cac dich vu (container)...
docker compose up -d kafka kafka-ui admin-db parking-db billing-db
docker compose up -d admin-service parking-service billing-service edge-agent

echo [3/4] Cho cac dich vu san sang (25 giay)...
timeout /t 25 /nobreak >nul

echo [4/4] Chuan bi chung chi HTTPS theo IP HIEN TAI roi khoi dong trang demo...
cd backend\edge-agent
".venv-alpr\Scripts\python.exe" "..\..\frontend\demo\setup_cert.py"
if errorlevel 10 (
  echo    IP LAN thay doi -^> tao lai parking-service de link anh MinIO tro dung host...
  cd /d "D:\SU26\MSS301\Group_Project\smart-parking-car"
  docker compose up -d parking-service
  cd backend\edge-agent
)
echo.
echo    (Giu cua so DEN nay mo trong luc demo. Dong cua so = tat trang demo.)
echo.

".venv-alpr\Scripts\python.exe" -m uvicorn server:app --app-dir "..\..\frontend\demo" --host 0.0.0.0 --port 8095 --ssl-keyfile "..\..\frontend\demo\certs\key.pem" --ssl-certfile "..\..\frontend\demo\certs\cert.pem"

echo.
echo Trang demo da dung. Bam phim bat ky de dong.
pause >nul
