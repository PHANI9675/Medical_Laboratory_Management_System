@echo off
title MedLab — Starting All Services
color 0B

echo.
echo  ============================================================
echo   MedLab Microservices — Full Auto-Startup
echo  ============================================================
echo.
echo  This will:
echo    1. Create MySQL databases if missing
echo    2. Set environment variables (auth, patient, notification)
echo    3. Start all 10 services in sequence, each in its own window
echo.

:: ── CONFIGURABLE ─────────────────────────────────────────────────────────────
:: Edit these if your MySQL credentials differ from the defaults
set MEDLAB_DB_USER=root
set MEDLAB_DB_PASSWORD=root
set MEDLAB_DB_HOST=localhost
set MEDLAB_DB_PORT=3306

:: ── CHANGE TO PROJECT DIRECTORY ──────────────────────────────────────────────
cd /d "%~dp0"

:: ── CHECK POWERSHELL ─────────────────────────────────────────────────────────
where powershell >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] PowerShell not found. Please install PowerShell 5+ and retry.
    pause
    exit /b 1
)

:: ── RUN THE PS1 SCRIPT ───────────────────────────────────────────────────────
:: -ExecutionPolicy Bypass lets the script run without changing system policy
:: -NoExit keeps the window open so you can read the output
powershell.exe -NoLogo -ExecutionPolicy Bypass -NoExit -File "%~dp0start-all.ps1" ^
    -DbUser "%MEDLAB_DB_USER%" ^
    -DbPassword "%MEDLAB_DB_PASSWORD%" ^
    -DbHost "%MEDLAB_DB_HOST%" ^
    -DbPort "%MEDLAB_DB_PORT%"

exit /b %errorlevel%
