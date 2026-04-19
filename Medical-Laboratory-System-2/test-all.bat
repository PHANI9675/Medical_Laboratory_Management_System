@echo off
:: ============================================================
::  MedLab API Test Runner  --  test-all.bat
::  Runs test-all.ps1 against already-running services.
::  Services must be started first (run start-all.bat).
:: ============================================================
::  Usage:
::    test-all.bat                   (uses root/root for MySQL)
::    test-all.bat myuser mypassword (custom MySQL credentials)
:: ============================================================

setlocal

set "DB_USER=root"
set "DB_PASS=root"

if not "%~1"=="" set "DB_USER=%~1"
if not "%~2"=="" set "DB_PASS=%~2"

echo.
echo  ============================================================
echo   MedLab API Test Runner
echo   Make sure all services are running before proceeding.
echo  ============================================================
echo.

powershell.exe ^
    -NoLogo ^
    -ExecutionPolicy Bypass ^
    -NoExit ^
    -File "%~dp0test-all.ps1" ^
    -DbUser "%DB_USER%" ^
    -DbPassword "%DB_PASS%"

endlocal
