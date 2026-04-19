#Requires -Version 5.0
<#
.SYNOPSIS
    MedLab Microservices - Stop All Services
.DESCRIPTION
    Finds and kills every process currently listening on a MedLab service port.
    Safe to run multiple times. Processes that are already stopped are ignored.
#>

$ports = @(
    @{ Port=8761; Name="Eureka Discovery Server" },
    @{ Port=8888; Name="Config Server" },
    @{ Port=8081; Name="Auth Service" },
    @{ Port=8086; Name="Patient Service" },
    @{ Port=8084; Name="Inventory Service" },
    @{ Port=8082; Name="Order Service" },
    @{ Port=8083; Name="Lab Processing Service (LPS)" },
    @{ Port=8087; Name="Notification Service" },
    @{ Port=8085; Name="Billing Service" },
    @{ Port=8090; Name="API Gateway" }
)

Write-Host ""
Write-Host "+--------------------------------------------------------------+" -ForegroundColor Yellow
Write-Host "|         MedLab Microservices - Stopping All Services         |" -ForegroundColor Yellow
Write-Host "+--------------------------------------------------------------+" -ForegroundColor Yellow
Write-Host ""

$stopped    = 0
$alreadyFree = 0

foreach ($svc in $ports) {
    $port = $svc.Port
    $name = $svc.Name

    # Find PID listening on this port using netstat
    $netstatLines = netstat -ano 2>$null | Where-Object {
        $_ -match "^\s+TCP\s+.*:$port\s+.*LISTENING"
    }

    if (-not $netstatLines) {
        Write-Host ("  [--]  {0,-40} port {1} - already free" -f $name, $port) -ForegroundColor DarkGray
        $alreadyFree++
        continue
    }

    # Extract the PID (last column of netstat output)
    $pids = $netstatLines | ForEach-Object {
        ($_ -split '\s+' | Where-Object { $_ -ne "" })[-1]
    } | Select-Object -Unique

    foreach ($procId in $pids) {
        if (-not $procId -or $procId -eq "0") { continue }
        try {
            $proc     = Get-Process -Id $procId -ErrorAction SilentlyContinue
            $procName = if ($proc) { $proc.Name } else { "PID $procId" }
            Stop-Process -Id $procId -Force -ErrorAction Stop
            Write-Host ("  [OK]  {0,-40} port {1} - stopped ({2})" -f $name, $port, $procName) -ForegroundColor Green
            $stopped++
        } catch {
            Write-Host ("  [!!]  {0,-40} port {1} - could not stop PID {2}: {3}" -f $name, $port, $procId, $_) -ForegroundColor Yellow
        }
    }
}

Write-Host ""
Write-Host "  Stopped     : $stopped process(es)" -ForegroundColor Green
Write-Host "  Already free: $alreadyFree port(s)" -ForegroundColor DarkGray
Write-Host ""

if ($stopped -gt 0) {
    Write-Host "  Waiting 3 seconds for ports to fully release..." -ForegroundColor Gray
    Start-Sleep -Seconds 3
    Write-Host "  Done. You can now run start-all.bat" -ForegroundColor Cyan
} else {
    Write-Host "  No services were running. Ready to run start-all.bat" -ForegroundColor Cyan
}
Write-Host ""
