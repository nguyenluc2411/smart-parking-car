@echo off
chcp 65001 >nul
title Smart Parking - DUNG DEMO
cd /d "D:\SU26\MSS301\Group_Project\smart-parking-car"

echo Dang tat trang demo (cong 8095)...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8095" ^| findstr "LISTENING"') do taskkill /PID %%a /F >nul 2>&1

echo Dang tat cac container...
docker compose stop

echo.
echo Da dung. (Du lieu van duoc giu lai, lan sau chay start-demo.bat la chay tiep.)
pause
