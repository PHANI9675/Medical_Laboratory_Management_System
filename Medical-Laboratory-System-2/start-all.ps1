#Requires -Version 5.0
<#
.SYNOPSIS
    MedLab Microservices - Full Auto-Startup Script
.DESCRIPTION
    Starts all 10 MedLab microservices in the correct order from zero.
    - Creates MySQL databases if they do not exist
    - Sets environment variables for auth-service, patient_service, Notification_service
    - Opens each service in its own CMD window with full log output
    - Waits for each service to be ready on its port before starting the next
.EXAMPLE
    .\start-all.ps1
    .\start-all.ps1 -DbPassword "yourpassword"
    .\start-all.ps1 -DbUser "admin" -DbPassword "secret" -TimeoutSecs 240
#>

param(
    [string]$DbUser      = "root",
    [string]$DbPassword  = "root",
    [string]$DbHost      = "localhost",
    [string]$DbPort      = "3306",
    [string]$JwtSecret   = "thisisverysecuresecretkeyforjwttokengenerationandvalidationteam5medlab",
    [int]   $TimeoutSecs = 180
)

# ----------------------------------------------------------------------------
#  COLOUR HELPERS
# ----------------------------------------------------------------------------
function Write-Step { param($msg) Write-Host "" ; Write-Host "==> $msg" -ForegroundColor Cyan }
function Write-OK   { param($msg) Write-Host "    [OK]  $msg" -ForegroundColor Green }
function Write-Warn { param($msg) Write-Host "    [!!]  $msg" -ForegroundColor Yellow }
function Write-Err  { param($msg) Write-Host "    [ERR] $msg" -ForegroundColor Red }
function Write-Info { param($msg) Write-Host "          $msg" -ForegroundColor Gray }

# ----------------------------------------------------------------------------
#  PATHS
# ----------------------------------------------------------------------------
$ROOT    = $PSScriptRoot
$BACKEND = Join-Path $ROOT "backend"
$TMPDIR  = [System.IO.Path]::GetTempPath()

# ----------------------------------------------------------------------------
#  FIND MAVEN EXECUTABLE
# ----------------------------------------------------------------------------
function Find-Maven {
    # 1. Already in PATH?
    $inPath = Get-Command "mvn" -ErrorAction SilentlyContinue
    if ($inPath) { return $inPath.Source }

    $inPathCmd = Get-Command "mvn.cmd" -ErrorAction SilentlyContinue
    if ($inPathCmd) { return $inPathCmd.Source }

    # 2. Standard environment variables
    foreach ($envVar in @("MAVEN_HOME", "M2_HOME", "MVN_HOME")) {
        $val = [System.Environment]::GetEnvironmentVariable($envVar)
        if ($val) {
            $candidate = Join-Path $val "bin\mvn.cmd"
            if (Test-Path $candidate) { return $candidate }
        }
    }

    # 3. Common fixed install locations
    $fixedPaths = @(
        "C:\Program Files\Apache Software Foundation\maven\bin\mvn.cmd",
        "C:\Program Files\Maven\bin\mvn.cmd",
        "C:\maven\bin\mvn.cmd",
        "C:\tools\maven\bin\mvn.cmd"
    )
    foreach ($p in $fixedPaths) {
        if (Test-Path $p) { return $p }
    }

    # 4. Search Desktop folders (local + OneDrive variants - covers team setups)
    $searchRoots = @(
        $env:USERPROFILE,
        (Join-Path $env:USERPROFILE "Desktop"),
        (Join-Path $env:USERPROFILE "OneDrive")
    )
    # Add "OneDrive - CompanyName" directories
    $oneDriveDirs = Get-ChildItem $env:USERPROFILE -Directory -ErrorAction SilentlyContinue |
                    Where-Object { $_.Name -like "OneDrive*" }
    foreach ($od in $oneDriveDirs) {
        $searchRoots += (Join-Path $od.FullName "Desktop")
        $searchRoots += $od.FullName
    }

    foreach ($searchRoot in $searchRoots) {
        if (-not (Test-Path $searchRoot)) { continue }
        $found = Get-ChildItem $searchRoot -Directory -ErrorAction SilentlyContinue |
                 Where-Object { $_.Name -like "apache-maven*" -or $_.Name -like "maven*" } |
                 Select-Object -First 1
        if ($found) {
            $candidate = Join-Path $found.FullName "bin\mvn.cmd"
            if (Test-Path $candidate) { return $candidate }
        }
    }

    return $null
}

# ----------------------------------------------------------------------------
#  FIND MYSQL EXECUTABLE
# ----------------------------------------------------------------------------
function Find-MySQL {
    # 1. In PATH?
    $inPath = Get-Command "mysql" -ErrorAction SilentlyContinue
    if ($inPath) { return $inPath.Source }

    # 2. Common install paths
    $candidates = @(
        "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe",
        "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe",
        "C:\Program Files\MySQL\MySQL Server 9.0\bin\mysql.exe",
        "C:\Program Files (x86)\MySQL\MySQL Server 8.0\bin\mysql.exe",
        "C:\MySQL\bin\mysql.exe",
        "C:\tools\mysql\bin\mysql.exe"
    )
    foreach ($c in $candidates) {
        if (Test-Path $c) { return $c }
    }

    # 3. Broad search under Program Files\MySQL (exclude Workbench)
    if (Test-Path "C:\Program Files\MySQL") {
        $found = Get-ChildItem "C:\Program Files\MySQL" -Recurse -Filter "mysql.exe" `
                     -ErrorAction SilentlyContinue |
                 Where-Object { $_.FullName -notlike "*Workbench*" } |
                 Select-Object -First 1
        if ($found) { return $found.FullName }
    }

    return $null
}

# ----------------------------------------------------------------------------
#  WAIT FOR TCP PORT TO ACCEPT CONNECTIONS
# ----------------------------------------------------------------------------
function Wait-ForPort {
    param(
        [int]$Port,
        [string]$Name,
        [int]$TimeoutSec = $TimeoutSecs
    )
    Write-Host "    Waiting for $Name on port $Port " -NoNewline
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    $dots = 0
    while ((Get-Date) -lt $deadline) {
        try {
            $tcp = New-Object System.Net.Sockets.TcpClient
            $ar  = $tcp.BeginConnect("127.0.0.1", $Port, $null, $null)
            $ok  = $ar.AsyncWaitHandle.WaitOne(1500, $false)
            if ($ok -and $tcp.Connected) {
                $tcp.Close()
                Write-Host " UP" -ForegroundColor Green
                Start-Sleep -Seconds 2
                return $true
            }
            $tcp.Close()
        } catch { }
        Write-Host "." -NoNewline
        $dots++
        if ($dots % 30 -eq 0) { Write-Host "" }
        Start-Sleep -Seconds 2
    }
    Write-Host " TIMED OUT" -ForegroundColor Red
    return $false
}

# ----------------------------------------------------------------------------
#  CHECK WHETHER A PORT IS FREE (NOT ALREADY LISTENING)
# ----------------------------------------------------------------------------
function Test-PortFree {
    param([int]$Port)
    try {
        $tcp = New-Object System.Net.Sockets.TcpClient
        $ar  = $tcp.BeginConnect("127.0.0.1", $Port, $null, $null)
        $ok  = $ar.AsyncWaitHandle.WaitOne(500, $false)
        if ($ok -and $tcp.Connected) { $tcp.Close(); return $false }
        $tcp.Close()
    } catch { }
    return $true
}

# ----------------------------------------------------------------------------
#  CREATE MySQL DATABASES
# ----------------------------------------------------------------------------
function Setup-Databases {
    param([string]$MySQLExe)

    $databases = @(
        "auth_db",
        "patient_db",
        "notification_db",
        "billing",
        "medlab",
        "lab_processing",
        "inventory_db"
    )

    if (-not $MySQLExe) {
        Write-Warn "MySQL CLI not found. Cannot create databases automatically."
        Write-Host ""
        Write-Host "  Please run the following SQL in MySQL Workbench (or any MySQL client):" `
            -ForegroundColor Yellow
        foreach ($db in $databases) {
            Write-Host "    CREATE DATABASE IF NOT EXISTS ``$db``;" -ForegroundColor Yellow
        }
        Write-Host ""
        Write-Host "  Press ENTER after creating the databases to continue..." -ForegroundColor Yellow
        Read-Host | Out-Null
        return
    }

    Write-Info "MySQL CLI : $MySQLExe"
    Write-Info "Connecting: $DbUser @ ${DbHost}:${DbPort}"

    # Test the connection before attempting anything
    $testOut = & "$MySQLExe" "-u$DbUser" "-p$DbPassword" "-h$DbHost" "-P$DbPort" `
                   "--connect-timeout=5" "-e" "SELECT 1;" 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Err "Cannot connect to MySQL at ${DbHost}:${DbPort} as user '$DbUser'."
        Write-Err "Details: $testOut"
        Write-Err ""
        Write-Err "Fix options:"
        Write-Err "  - Ensure MySQL service is running (check Services / Task Manager)"
        Write-Err "  - Pass correct credentials: .\start-all.ps1 -DbUser root -DbPassword yourpassword"
        exit 1
    }

    foreach ($db in $databases) {
        $out = & "$MySQLExe" "-u$DbUser" "-p$DbPassword" "-h$DbHost" "-P$DbPort" `
                   "-e" "CREATE DATABASE IF NOT EXISTS ``$db``;" 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-OK "Database '$db' is ready"
        } else {
            Write-Warn "Could not create '$db': $out"
        }
    }
}

# ----------------------------------------------------------------------------
#  LAUNCH A SERVICE IN ITS OWN CMD WINDOW
# ----------------------------------------------------------------------------
function Start-Service {
    param(
        [string]$Name,
        [string]$Dir,
        [int]$Port,
        [hashtable]$EnvVars = @{}
    )

    # Prefer the Maven wrapper inside the service directory; fall back to system Maven
    $mvnCmd = if (Test-Path (Join-Path $Dir "mvnw.cmd")) {
        "mvnw.cmd"
    } else {
        "`"$script:MvnExe`""
    }

    # Build a temporary batch file for this service
    $lines = @(
        "@echo off",
        "title $Name  [port $Port]",
        "color 0A",
        "echo ============================================================",
        "echo  Service : $Name",
        "echo  Port    : $Port",
        "echo ============================================================",
        "echo.",
        "cd /d `"$Dir`""
    )

    foreach ($kv in $EnvVars.GetEnumerator()) {
        $lines += "set $($kv.Key)=$($kv.Value)"
    }

    $lines += "$mvnCmd spring-boot:run"
    $lines += "echo."
    $lines += "echo [SERVICE STOPPED] Press any key to close..."
    $lines += "pause > nul"

    $batContent = $lines -join "`r`n"

    # Safe filename: strip non-alphanumeric characters
    $safeName = $Name -replace '[^a-zA-Z0-9]', '_'
    $batPath  = Join-Path $TMPDIR "medlab_$safeName.bat"
    [System.IO.File]::WriteAllText($batPath, $batContent, [System.Text.Encoding]::ASCII)

    Start-Process "cmd.exe" -ArgumentList "/k", "`"$batPath`""
}

# ----------------------------------------------------------------------------
#  SERVICE DEFINITIONS (startup order matches guide.md Section 5)
# ----------------------------------------------------------------------------
$DB_OPTS = "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"

$services = @(
    @{
        Num  = 1
        Name = "Eureka Discovery Server"
        Dir  = Join-Path $BACKEND "server"
        Port = 8761
        Env  = @{}
    },
    @{
        Num  = 2
        Name = "Config Server"
        Dir  = Join-Path $BACKEND "config-server"
        Port = 8888
        Env  = @{}
    },
    @{
        Num  = 3
        Name = "Auth Service"
        Dir  = Join-Path $BACKEND "auth-service"
        Port = 8081
        Env  = @{
            DB_URL      = "jdbc:mysql://${DbHost}:${DbPort}/auth_db${DB_OPTS}"
            DB_USER     = $DbUser
            DB_PASSWORD = $DbPassword
            JWT_SECRET  = $JwtSecret
        }
    },
    @{
        Num  = 4
        Name = "Patient Service"
        Dir  = Join-Path $BACKEND "patient_service"
        Port = 8086
        Env  = @{
            DB_URL      = "jdbc:mysql://${DbHost}:${DbPort}/patient_db${DB_OPTS}"
            DB_USER     = $DbUser
            DB_PASSWORD = $DbPassword
            JWT_SECRET  = $JwtSecret
        }
    },
    @{
        Num  = 5
        Name = "Inventory Service"
        Dir  = Join-Path $BACKEND "inventory-service"
        Port = 8084
        Env  = @{}
    },
    @{
        Num  = 6
        Name = "Order Service"
        Dir  = Join-Path $BACKEND "order-service"
        Port = 8082
        Env  = @{}
    },
    @{
        Num  = 7
        Name = "Lab Processing Service"
        Dir  = Join-Path $BACKEND "lps"
        Port = 8083
        Env  = @{}
    },
    @{
        Num  = 8
        Name = "Notification Service"
        Dir  = Join-Path $BACKEND "Notification_service"
        Port = 8087
        Env  = @{
            DB_URL      = "jdbc:mysql://${DbHost}:${DbPort}/notification_db${DB_OPTS}"
            DB_USER     = $DbUser
            DB_PASSWORD = $DbPassword
            JWT_SECRET  = $JwtSecret
        }
    },
    @{
        Num  = 9
        Name = "Billing Service"
        Dir  = Join-Path $BACKEND "billing-service"
        Port = 8085
        Env  = @{}
    },
    @{
        Num  = 10
        Name = "API Gateway"
        Dir  = Join-Path $BACKEND "api-gateway"
        Port = 8090
        Env  = @{}
    }
)

# ============================================================================
#  MAIN
# ============================================================================

Clear-Host
Write-Host "+--------------------------------------------------------------+" -ForegroundColor Cyan
Write-Host "|   MedLab Microservices - Full Auto-Startup                   |" -ForegroundColor Cyan
Write-Host "|   10 services | MySQL setup | Env vars handled               |" -ForegroundColor Cyan
Write-Host "+--------------------------------------------------------------+" -ForegroundColor Cyan
Write-Host ""

# -- Step 1: Find Maven -------------------------------------------------------
Write-Step "Locating Maven..."
$MvnExe = Find-Maven
if (-not $MvnExe) {
    Write-Err "Maven (mvn/mvn.cmd) not found in PATH or common locations."
    Write-Err "Fix options:"
    Write-Err "  1. Add Maven's bin folder to your PATH and restart this script"
    Write-Err "  2. Set the MAVEN_HOME environment variable to your Maven install folder"
    Write-Err "  3. Place Maven in one of: C:\maven, C:\tools\maven, or Desktop"
    exit 1
}
Write-OK "Maven: $MvnExe"

# -- Step 2: Check Java -------------------------------------------------------
Write-Step "Checking Java..."
$javaOut = java -version 2>&1 | Select-Object -First 1
if (-not $javaOut) {
    Write-Err "Java not found. Install Java 21 and add it to PATH."
    exit 1
}
Write-OK "Java: $javaOut"

# -- Step 3: MySQL databases --------------------------------------------------
Write-Step "Setting up MySQL databases..."
$MySQLExe = Find-MySQL
if ($MySQLExe) {
    Write-OK "MySQL CLI: $MySQLExe"
} else {
    Write-Warn "MySQL CLI not found automatically."
}
Setup-Databases -MySQLExe $MySQLExe

# -- Step 4: Validate directories ---------------------------------------------
Write-Step "Validating service directories..."
$allOk = $true
foreach ($svc in $services) {
    if (Test-Path $svc.Dir) {
        Write-OK "[$($svc.Num)] $($svc.Name)"
    } else {
        Write-Err "[$($svc.Num)] $($svc.Name) - NOT FOUND: $($svc.Dir)"
        $allOk = $false
    }
}
if (-not $allOk) {
    Write-Err "One or more service directories are missing. Cannot continue."
    exit 1
}

# -- Step 5: Port conflict check ----------------------------------------------
Write-Step "Checking for port conflicts..."
$anyConflict = $false
foreach ($svc in $services) {
    if (-not (Test-PortFree -Port $svc.Port)) {
        Write-Warn "Port $($svc.Port) already in use  ($($svc.Name))"
        Write-Warn "  --> Run stop-all.bat first to free all ports."
        $anyConflict = $true
    }
}
if ($anyConflict) {
    Write-Host ""
    $answer = Read-Host "One or more ports are already in use. Continue anyway? (y/N)"
    if ($answer -notmatch '^[Yy]') {
        Write-Host "Aborted. Run stop-all.bat first, then retry." -ForegroundColor Yellow
        exit 0
    }
}

# -- Step 6: Launch services --------------------------------------------------
Write-Step "Launching services (each opens in its own window)..."
Write-Info "NOTE: First run downloads Maven dependencies - may take 5 to 15 minutes per service."
Write-Info "      Subsequent runs start in about 30 seconds each."
Write-Host ""

$failed = @()
foreach ($svc in $services) {
    Write-Host "  [$($svc.Num)/10] $($svc.Name) on port $($svc.Port)..." -ForegroundColor White

    Start-Service -Name $svc.Name -Dir $svc.Dir -Port $svc.Port -EnvVars $svc.Env

    $ready = Wait-ForPort -Port $svc.Port -Name $svc.Name
    if (-not $ready) {
        Write-Err "$($svc.Name) did not respond within $TimeoutSecs seconds."
        Write-Err "Check its CMD window for errors."
        $failed += $svc.Name

        if ($svc.Port -in @(8761, 8888)) {
            Write-Err "CRITICAL: This service is required by all others. Cannot continue."
            Write-Err "Fix the error shown in its window, then re-run start-all.bat."
            exit 1
        }

        $cont = Read-Host "Continue starting remaining services? (y/N)"
        if ($cont -notmatch '^[Yy]') {
            Write-Host "Startup stopped by user." -ForegroundColor Yellow
            exit 1
        }
    } else {
        Write-OK "$($svc.Name) is UP"
    }
    Write-Host ""
}

# -- Step 7: Summary ----------------------------------------------------------
Write-Host ""
Write-Host "+--------------------------------------------------------------+" -ForegroundColor Cyan
Write-Host "|                    STARTUP COMPLETE                         |" -ForegroundColor Cyan
Write-Host "+--------------------------------------------------------------+" -ForegroundColor Cyan
Write-Host ""

foreach ($svc in $services) {
    $mark   = if ($svc.Name -in $failed) { "FAILED" } else { "UP    " }
    $colour = if ($svc.Name -in $failed) { "Red"    } else { "Green" }
    Write-Host ("  [{0}]  {1,-42} port {2}" -f $mark, $svc.Name, $svc.Port) -ForegroundColor $colour
}

Write-Host ""
Write-Host "  Eureka dashboard : http://localhost:8761" -ForegroundColor Cyan
Write-Host "  Swagger (all)    : http://localhost:8090/swagger-ui.html" -ForegroundColor Cyan
Write-Host ""

if ($failed.Count -gt 0) {
    Write-Warn "Failed services: $($failed -join ', ')"
    Write-Warn "Check each service window for the error, fix it, then restart that service manually."
} else {
    Write-OK "All 10 services are running."
    Write-Host ""
    Write-Host "  NEXT STEPS (guide.md Section 6):" -ForegroundColor White
    Write-Host "  1. Register users  -> POST http://localhost:8090/auth/register" -ForegroundColor Gray
    Write-Host "  2. Assign roles    -> SQL: UPDATE auth_db.users SET role='ADMIN' WHERE username='admin@lab.com';" -ForegroundColor Gray
    Write-Host "  3. Add lab test    -> POST http://localhost:8090/tests  (ADMIN token required)" -ForegroundColor Gray
    Write-Host "  4. Full E2E flow   -> Follow Section 8 of guide.md" -ForegroundColor Gray
    Write-Host ""
}

Write-Host "  To stop all services: run stop-all.bat" -ForegroundColor DarkGray
Write-Host ""
