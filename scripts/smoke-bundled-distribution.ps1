# Smokes an extracted Windows bundled-runtime archive under the desktop
# profile with no JAVA_HOME and no Java on PATH. Windows has no graceful
# console stop from a script, so the database-close check is manual
# (alpha-acceptance.md).
param(
  [Parameter(Mandatory = $true)][string]$AppDirectory,
  [Parameter(Mandatory = $true)][int]$Port
)
$ErrorActionPreference = 'Stop'
function Fail([string]$Message) { throw "bundled distribution smoke failed: $Message" }

$app = (Resolve-Path -LiteralPath $AppDirectory).Path
$bundledJava = Join-Path $app 'jre\bin\java.exe'
if (-not (Test-Path -LiteralPath $bundledJava)) { Fail "missing bundled jre\ in $app" }
if (-not (Test-Path -LiteralPath (Join-Path $app 'Start Oath Digital.bat'))) { Fail 'missing Start Oath Digital.bat' }

$temporary = Join-Path ([IO.Path]::GetTempPath()) ('oathdigital-bundled-' + [guid]::NewGuid())
New-Item -ItemType Directory -Path $temporary | Out-Null
$log = Join-Path $temporary 'server.log'
$errorLog = Join-Path $temporary 'server.err.log'
$appData = Join-Path $temporary 'local\OathDigital'

$env:LOCALAPPDATA = Join-Path $temporary 'local'
Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
$env:PATH = "$env:SystemRoot\System32;$env:SystemRoot"
$env:OATH_LAUNCH = 'desktop'
$env:OATH_OPEN_BROWSER = 'false'
$env:OATH_PORT = "$Port"

$process = Start-Process -FilePath (Join-Path $app 'bin\oathdigital.bat') -NoNewWindow -PassThru `
  -RedirectStandardOutput $log -RedirectStandardError $errorLog
try {
  $url = $null
  for ($i = 0; $i -lt 90 -and -not $url; $i++) {
    Start-Sleep -Seconds 1
    if ($process.HasExited) { Fail "server exited before the banner with code $($process.ExitCode)" }
    if (Test-Path -LiteralPath $log) {
      $match = Select-String -LiteralPath $log -Pattern '^\s+(Players open:|Only this computer can connect:)\s+(http://\S+)' |
        Select-Object -First 1
      if ($match) { $url = $match.Matches[0].Groups[2].Value }
    }
  }
  if (-not $url) { Fail 'banner did not appear within 90 seconds' }
  if (-not $url.EndsWith(":$Port")) { Fail "banner address $url does not use port $Port" }
  foreach ($path in '/health/ready', '/', '/assets/main.js') {
    $response = Invoke-WebRequest -UseBasicParsing -TimeoutSec 10 -Uri "$url$path"
    if ($response.StatusCode -ne 200) { Fail "$url$path returned $($response.StatusCode)" }
  }
  $java = Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
    Where-Object { $_.ExecutablePath -ieq $bundledJava }
  if (-not $java) { Fail 'server is not running on the bundled runtime' }
  if (-not (Test-Path -LiteralPath (Join-Path $appData 'oathdigital.properties'))) { Fail 'settings file was not created' }
  if (-not (Get-ChildItem -LiteralPath (Join-Path $appData 'data') -Filter 'database.*' -ErrorAction SilentlyContinue)) {
    Fail 'database was not created in the app-data folder'
  }
  Write-Output "bundled distribution smoke passed: $app"
}
finally {
  Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
    Where-Object { $_.ExecutablePath -ieq $bundledJava } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
  if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force }
  Remove-Item -LiteralPath $temporary -Recurse -Force -ErrorAction SilentlyContinue
}
