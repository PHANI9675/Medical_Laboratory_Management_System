@echo off
:: ============================================================
::  MedLab Debug / Full Test  --  debug.bat
::  1. Starts all microservices (via start-all.ps1)
::  2. Waits up to 120 seconds for services to be ready
::     (press ENTER at any time to skip the wait)
::  3. Runs the full automated API test suite (test-all.ps1)
:: ============================================================
::  Usage:
::    debug.bat                   (MySQL root/root)
::    debug.bat myuser mypassword (custom credentials)
:: ============================================================

setlocal

set "DB_USER=root"
set "DB_PASS=root"

if not "%~1"=="" set "DB_USER=%~1"
if not "%~2"=="" set "DB_PASS=%~2"

echo.
echo  ============================================================
echo   MedLab Debug Runner  --  Step 1: Start all services
echo  ============================================================
echo.

:: start-all.ps1 launches each service in its own window and returns
:: after kicking them all off, so this call completes quickly.
powershell.exe ^
    -NoLogo ^
    -ExecutionPolicy Bypass ^
    -File "%~dp0start-all.ps1" ^
    -DbUser "%DB_USER%" ^
    -DbPassword "%DB_PASS%"

echo.
echo  ============================================================
echo   Step 2: Waiting for services to be ready
echo   Press ENTER to skip the wait, or it will auto-continue
echo   after 120 seconds.
echo  ============================================================
echo.

:: Interruptible countdown: press Enter to skip, otherwise auto-
:: continues after 120 seconds. Uses [Console]::KeyAvailable so
:: the loop is non-blocking and the countdown updates in-place.
powershell.exe -NoLogo -ExecutionPolicy Bypass -Command ^
    "$max = 120;" ^
    "$sw  = [System.Diagnostics.Stopwatch]::StartNew();" ^
    "Write-Host '  Press ENTER to skip, or wait 120s...' -ForegroundColor Yellow;" ^
    "while ($sw.Elapsed.TotalSeconds -lt $max) {" ^
        "$remaining = $max - [int]$sw.Elapsed.TotalSeconds;" ^
        "Write-Host (\"`r  Continuing in $remaining s...   \") -NoNewline -ForegroundColor DarkGray;" ^
        "if ([Console]::KeyAvailable) {" ^
            "$k = [Console]::ReadKey($true);" ^
            "if ($k.Key -eq 'Enter') { break }" ^
        "}" ^
        "Start-Sleep -Milliseconds 300;" ^
    "}" ^
    "Write-Host '' ; Write-Host '  Proceeding.' -ForegroundColor Green"

echo.
echo  ============================================================
echo   Step 3: Running API test suite
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
