param(
  [int]$Port = 8080,
  [switch]$Rebuild = $false,
  [switch]$NoBrowser = $false,
  [switch]$NoLogFile = $false
)

# Runs the packaged app in single-port "prod" mode: one Spring Boot process
# serves both the API and the pre-built frontend on a free port. Logs stream to
# this console (and, by default, to logs/prod-<timestamp>.log). The dev flow
# (start-dev.cmd) is separate and unaffected.

$ErrorActionPreference = 'Stop'

# --- Toolchain: the JDK is not on PATH on this machine ----------------------
# Prefer the known JDK 17 — the ambient JAVA_HOME on this machine is IBM Java 8.
$JdkHome = if (Test-Path 'C:\Users\bob.zhu\jdk-17.0.19+10\bin\java.exe') {
  'C:\Users\bob.zhu\jdk-17.0.19+10'
} elseif ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
  $env:JAVA_HOME
} else { $null }
if ($JdkHome) { $env:JAVA_HOME = $JdkHome; $env:Path = (Join-Path $JdkHome 'bin') + ';' + $env:Path }

function Test-PortFree {
  param([int]$Port)
  $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($conn) { return $false }
  try {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $Port)
    $listener.Start()
    $listener.Stop()
    return $true
  } catch {
    return $false
  }
}

function Get-FreePort {
  param([int]$StartPort, [int]$MaxTries = 50)
  for ($i = 0; $i -lt $MaxTries; $i++) {
    $candidate = $StartPort + $i
    if (Test-PortFree -Port $candidate) { return $candidate }
  }
  throw "No free port found in range $StartPort..$($StartPort + $MaxTries - 1)"
}

$scriptRoot  = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendDir  = Join-Path $scriptRoot 'backend'
$frontendDir = Join-Path $scriptRoot 'frontend'
$distDir     = Join-Path $frontendDir 'dist'

if (-not (Test-Path $backendDir)) { throw "Backend directory not found: $backendDir" }

# --- Locate the fat jar; build if missing or -Rebuild -----------------------
function Get-Jar {
  Get-ChildItem (Join-Path $backendDir 'target') -Filter 'gammonscope-backend-*.jar' -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notlike '*.original' } |
    Select-Object -First 1
}

$jar = Get-Jar
if ($Rebuild -or -not $jar -or -not (Test-Path $distDir)) {
  Write-Host "Building production artifacts (first run or -Rebuild)..." -ForegroundColor Yellow
  & (Join-Path $scriptRoot 'build-prod.ps1')
  if ($LASTEXITCODE -ne 0) { throw "build-prod failed" }
  $jar = Get-Jar
}
if (-not $jar) { throw "Jar not found; build failed." }
if (-not (Test-Path $distDir)) { throw "Frontend dist not found: $distDir. Run build-prod.cmd first." }

# --- Resolve a free port ----------------------------------------------------
$requestedPort = $Port
$Port = Get-FreePort -StartPort $Port
if ($Port -ne $requestedPort) {
  Write-Host "Port $requestedPort is in use, falling back to $Port" -ForegroundColor Yellow
}

# Persist the resolved port so it can be reused / stopped later.
$stateFile = Join-Path $scriptRoot '.gs-prod-state.json'
@{
  port      = $Port
  startedAt = (Get-Date).ToString('o')
} | ConvertTo-Json | Set-Content -Path $stateFile -Encoding UTF8

# --- Optional log file (console always streams regardless) ------------------
$logFile = $null
if (-not $NoLogFile) {
  $logsDir = Join-Path $scriptRoot 'logs'
  if (-not (Test-Path $logsDir)) { New-Item -ItemType Directory -Path $logsDir | Out-Null }
  $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
  $logFile = Join-Path $logsDir "prod-$stamp.log"
}

# --- Open the browser once the port is listening (background) ---------------
if (-not $NoBrowser) {
  Start-Job -Name 'gs-prod-open' -ScriptBlock {
    param([int]$Port)
    for ($i = 0; $i -lt 180; $i++) {
      $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
      if ($conn) { Start-Process "http://localhost:$Port"; break }
      Start-Sleep -Seconds 1
    }
  } -ArgumentList $Port | Out-Null
}

$javaExe = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
if ($env:JAVA_HOME -and -not (Test-Path $javaExe)) { $javaExe = 'java' }

Write-Host ""
Write-Host "===============================================" -ForegroundColor Cyan
Write-Host " GammonScope (prod) starting" -ForegroundColor Cyan
Write-Host "   URL     : http://localhost:$Port" -ForegroundColor Green
Write-Host "   Jar     : $($jar.FullName)"
Write-Host "   Frontend: $distDir"
if ($logFile) { Write-Host "   Log     : $logFile" }
Write-Host " Press Ctrl+C to stop." -ForegroundColor Cyan
Write-Host "===============================================" -ForegroundColor Cyan
Write-Host ""

# Run in the foreground with cwd = backend so H2 (./data) resolves exactly like
# the dev flow. Program args (not env) carry the port + dist so they always
# reach Spring reliably.
Push-Location $backendDir
try {
  $jarArgs = @(
    '-jar', $jar.FullName,
    "--server.port=$Port",
    "--app.web.dist=$distDir"
  )
  if ($logFile) {
    & $javaExe @jarArgs 2>&1 | Tee-Object -FilePath $logFile
  } else {
    & $javaExe @jarArgs 2>&1
  }
} finally {
  Pop-Location
  Get-Job -Name 'gs-prod-open' -ErrorAction SilentlyContinue | Remove-Job -Force -ErrorAction SilentlyContinue
  if (Test-Path $stateFile) { Remove-Item $stateFile -ErrorAction SilentlyContinue }
}
