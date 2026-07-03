@echo off
setlocal EnableExtensions
chcp 65001 >nul
title Smart Parking - DUNG DEMO

REM Luon dung thu muc chua file .bat — khong hardcode duong dan may khac.
set "PROJECT_ROOT=%~dp0"
if "%PROJECT_ROOT:~-1%"=="\" set "PROJECT_ROOT=%PROJECT_ROOT:~0,-1%"
cd /d "%PROJECT_ROOT%"

echo Thu muc project: %PROJECT_ROOT%
echo.
echo Dang tat trang demo cong 8095...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8095" ^| findstr "LISTENING"') do taskkill /PID %%a /F >nul 2>&1

echo Dang tat cac container...
docker compose -f "%PROJECT_ROOT%\docker-compose.yml" stop

echo.
echo Da dung. Du lieu van duoc giu lai, lan sau chay start-demo.bat la chay tiep.
pause
