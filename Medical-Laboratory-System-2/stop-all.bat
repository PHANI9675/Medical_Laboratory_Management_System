@echo off
title MedLab — Stopping All Services
color 0E

echo.
echo  ============================================================
echo   MedLab Microservices — Stop All Services
echo  ============================================================
echo.

where powershell >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] PowerShell not found.
    pause
    exit /b 1
)

powershell.exe -NoLogo -ExecutionPolicy Bypass -NoExit -File "%~dp0stop-all.ps1"

exit /b %errorlevel%
