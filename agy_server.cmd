@echo off
setlocal enabledelayedexpansion
title AGY Remote Server

set "WORKDIR=c:\Users\qwusv\Documents\antigravity\zealous-tesla"
cd /d "%WORKDIR%"

if /i "%~1"=="/silent" goto silent_boot
if /i "%~1"=="-s" goto silent_boot

:: 1. AUTO-START (Tu dong bat nhanh neu chua chay)
netstat -ano | findstr /R /C:":4401 .*LISTENING" >nul 2>&1
if errorlevel 1 (
    schtasks /Run /TN "AgyRemoteControl" >nul 2>&1
)

netstat -ano | findstr /R /C:":4400 .*LISTENING" >nul 2>&1
if errorlevel 1 (
    start "AGY_LAN_BRIDGE" /min cmd /c "cd /d %WORKDIR% && node agy_lan_bridge.js"
)

tasklist /FI "IMAGENAME eq python.exe" /V 2>nul | findstr /I "sync_daemon.py" >nul 2>&1
if errorlevel 1 (
    start "AGY_REALTIME_SYNC" /min cmd /c "cd /d %WORKDIR% && python sync_daemon.py"
)

:: 2. DASHBOARD QUAN LY
:menu
cls
echo ======================================================================
echo             *** TRUNG TAM DIEU KHIEN AGY REMOTE SERVER ***
echo ======================================================================
echo.

set "STATUS_4401=[DA DUNG]"
netstat -ano | findstr /R /C:":4401 .*LISTENING" >nul 2>&1
if not errorlevel 1 set "STATUS_4401=[DANG HOAT DONG]"

set "STATUS_4400=[DA DUNG]"
netstat -ano | findstr /R /C:":4400 .*LISTENING" >nul 2>&1
if not errorlevel 1 set "STATUS_4400=[DANG HOAT DONG]"

set "STATUS_SYNC=[DA DUNG]"
tasklist /FI "IMAGENAME eq python.exe" /V 2>nul | findstr /I "realtime_sync.py" >nul 2>&1
if not errorlevel 1 set "STATUS_SYNC=[DANG HOAT DONG]"

set "LAN_IP=192.168.1.220"

echo  (1) Antigravity Daemon (Port 4401) : %STATUS_4401%
echo  (2) LAN Bridge Proxy   (Port 4400) : %STATUS_4400%
echo  (3) Tu Dong Dong Bo Du Lieu        : %STATUS_SYNC%
echo ----------------------------------------------------------------------
echo  * DIA CHI TREN DIEN THOAI         : http://%LAN_IP%:4400
echo ======================================================================
echo.
echo   [1] KHOI DONG LAI TAT CA (Restart All)
echo   [2] DUNG SERVER (Stop All)
echo   [3] MO WEB TREN MAY TINH (Browser)
echo   [4] KET NOI LAI DIEN THOAI (ADB 192.168.1.225:5555)
echo   [0] THOAT CUA SO (Server van tiep tuc chay ngam)
echo.
echo ======================================================================
set "CHOICE="
set /p "CHOICE=>> Nhap lua chon cua ban [0-4]: "

if "%CHOICE%"=="1" goto restart_all
if "%CHOICE%"=="2" goto stop_all
if "%CHOICE%"=="3" goto open_web
if "%CHOICE%"=="4" goto reconnect_adb
if "%CHOICE%"=="0" exit /b
goto menu

:restart_all
echo.
echo [*] Dang khoi dong lai toan bo dich vu...
powershell -Command "Stop-Process -Name node -ErrorAction SilentlyContinue; Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -match 'sync_daemon.py' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }"
schtasks /Run /TN "AgyRemoteControl" >nul 2>&1
timeout /t 2 /nobreak >nul
start "AGY_LAN_BRIDGE" /min cmd /c "cd /d %WORKDIR% && node agy_lan_bridge.js"
start "AGY_REALTIME_SYNC" /min cmd /c "cd /d %WORKDIR% && python sync_daemon.py"
timeout /t 2 /nobreak >nul
goto menu

:stop_all
echo.
echo [*] Dang dung tat ca dich vu AGY Server...
powershell -Command "Stop-Process -Name node -ErrorAction SilentlyContinue; Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -match 'sync_daemon.py' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }"
echo [V] Da dung server!
timeout /t 2 /nobreak >nul
goto menu

:open_web
start http://127.0.0.1:4401
goto menu

:reconnect_adb
echo.
echo [*] Dang ket noi ADB toi 192.168.1.225:5555...
adb connect 192.168.1.225:5555
timeout /t 2 /nobreak >nul
goto menu

:silent_boot
schtasks /Run /TN "AgyRemoteControl" >nul 2>&1
netstat -ano | findstr /R /C:":4400 .*LISTENING" >nul 2>&1
if errorlevel 1 (
    start "AGY_LAN_BRIDGE" /min cmd /c "cd /d %WORKDIR% && node agy_lan_bridge.js"
)
tasklist /FI "IMAGENAME eq python.exe" /V 2>nul | findstr /I "realtime_sync.py" >nul 2>&1
if errorlevel 1 (
    start "AGY_REALTIME_SYNC" /min cmd /c "cd /d %WORKDIR% && python realtime_sync.py"
)
exit /b
