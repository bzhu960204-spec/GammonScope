param(
  [switch]$SkipFrontend = $false
)

# Builds the production artifacts for the single-port "prod" flow:
#   1) builds the React frontend  -> frontend/dist
#   2) packages the Spring Boot fat jar -> backend/target/*.jar
# Run this after changing frontend or backend code. The dev flow (start-dev.cmd)
# is unaffected — this only produces the artifacts that start-prod.cmd runs.

$ErrorActionPreference = 'Stop'

# --- Toolchain: Node and the JDK are not on PATH on this machine -------------
# Prefer the known JDK 17 — the ambient JAVA_HOME on this machine is IBM Java 8.
$JdkHome = if (Test-Path 'C:\Users\bob.zhu\jdk-17.0.19+10\bin\java.exe') {
  'C:\Users\bob.zhu\jdk-17.0.19+10'
} elseif ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
  $env:JAVA_HOME
} else { $null }

$NodeDir = if (Get-Command npm.cmd -ErrorAction SilentlyContinue) {
  Split-Path (Get-Command npm.cmd).Source
} elseif (Test-Path 'C:\Users\bob.zhu\node-v24.14.1-win-x64\npm.cmd') {
  'C:\Users\bob.zhu\node-v24.14.1-win-x64'
} else { $null }

if ($JdkHome) { $env:JAVA_HOME = $JdkHome; $env:Path = (Join-Path $JdkHome 'bin') + ';' + $env:Path }
if ($NodeDir) { $env:Path = $NodeDir + ';' + $env:Path }

$scriptRoot  = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendDir  = Join-Path $scriptRoot 'backend'
$frontendDir = Join-Path $scriptRoot 'frontend'

if (-not (Test-Path $backendDir))  { throw "Backend directory not found: $backendDir" }
if (-not (Test-Path $frontendDir)) { throw "Frontend directory not found: $frontendDir" }

if (-not $SkipFrontend) {
  Write-Host "Building frontend..." -ForegroundColor Cyan
  Push-Location $frontendDir
  try {
    if (-not (Test-Path 'node_modules')) {
      & npm.cmd install
      if ($LASTEXITCODE -ne 0) { throw "npm install failed" }
    }
    & npm.cmd run build
    if ($LASTEXITCODE -ne 0) { throw "Frontend build failed" }
  } finally {
    Pop-Location
  }
} else {
  Write-Host "Skipping frontend build (-SkipFrontend)." -ForegroundColor DarkGray
}

Write-Host "Packaging backend fat jar..." -ForegroundColor Cyan
Push-Location $backendDir
try {
  & mvn -q -DskipTests clean package
  if ($LASTEXITCODE -ne 0) { throw "Backend package failed" }
} finally {
  Pop-Location
}

$jar = Get-ChildItem (Join-Path $backendDir 'target') -Filter 'gammonscope-backend-*.jar' -ErrorAction SilentlyContinue |
  Where-Object { $_.Name -notlike '*.original' } |
  Select-Object -First 1
if (-not $jar) { throw "Jar not found under backend/target" }

Write-Host ""
Write-Host "===============================================" -ForegroundColor Green
Write-Host " Production artifacts ready" -ForegroundColor Green
Write-Host "   Jar     : $($jar.FullName)"
Write-Host "   Frontend: $(Join-Path $frontendDir 'dist')"
Write-Host " Launch with start-prod.cmd" -ForegroundColor Green
Write-Host "===============================================" -ForegroundColor Green
