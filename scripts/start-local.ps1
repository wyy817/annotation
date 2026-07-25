[CmdletBinding()]
param(
    [string]$DataRoot = "",
    [string]$RelayConfigPath = "",
    [string]$LlmModelOverride = "",
    [string]$CondaEnvironment = "parvisight-py",
    [string]$SamCheckpoint = "",
    [ValidateSet("vit_t", "vit_b", "vit_l", "vit_h")]
    [string]$SamModelType = "vit_t",
    [switch]$DisableLlm
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$workspaceRoot = Split-Path $repoRoot -Parent

if (-not $DataRoot) {
    $DataRoot = Join-Path $workspaceRoot "PARVISIGHTData"
    $legacyDataRoot = Join-Path $workspaceRoot "PathorchestraData"
    if (-not (Test-Path -LiteralPath $DataRoot) -and (Test-Path -LiteralPath $legacyDataRoot)) {
        Write-Warning "Using legacy data directory: $legacyDataRoot"
        $DataRoot = $legacyDataRoot
    }
}
if (-not (Test-Path -LiteralPath $DataRoot)) {
    throw "Data directory not found: $DataRoot"
}
$DataRoot = (Resolve-Path $DataRoot).Path

if (-not $SamCheckpoint) {
    $SamCheckpoint = Join-Path $repoRoot "python-service\model\mobile_sam.pt"
}

if (-not $RelayConfigPath) {
    $RelayConfigPath = Get-ChildItem -LiteralPath $workspaceRoot -Filter "*.txt" -File |
        Where-Object {
            $content = Get-Content -LiteralPath $_.FullName -Raw
            $content -match "(?im)^\s*Base URL\s*:" -and
                $content -match "(?im)^\s*api key\s*:" -and
                $content -match "(?im)^\s*model\s*:"
        } |
        Select-Object -First 1 -ExpandProperty FullName
}

$runDir = Join-Path $repoRoot ".run"
New-Item -ItemType Directory -Path $runDir -Force | Out-Null

function Test-Port {
    param([int]$Port)

    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $connection = $client.ConnectAsync("127.0.0.1", $Port)
        return $connection.Wait(250) -and $client.Connected
    }
    catch {
        return $false
    }
    finally {
        $client.Dispose()
    }
}

function Wait-Port {
    param(
        [string]$Name,
        [int]$Port,
        [System.Diagnostics.Process]$Process,
        [int]$TimeoutSeconds = 90
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if ($Process -and $Process.HasExited) {
            throw "$Name exited during startup. Check .run logs."
        }
        if (Test-Port $Port) {
            Write-Host "$Name ready on port $Port"
            return
        }
        Start-Sleep -Milliseconds 500
    }
    throw "$Name did not become ready on port $Port. Check .run logs."
}

function Get-ListeningProcessId {
    param([int]$Port)

    foreach ($line in (& netstat -ano -p tcp)) {
        $columns = $line.Trim() -split "\s+"
        if ($columns.Count -ne 5 -or $columns[0] -ne "TCP" -or $columns[3] -ne "LISTENING") {
            continue
        }
        if ($columns[1] -match ":$Port$") {
            return [int]$columns[4]
        }
    }
    return $null
}

function Start-LocalProcess {
    param(
        [string]$Name,
        [int]$Port,
        [string]$FilePath,
        [string[]]$ArgumentList,
        [string]$WorkingDirectory
    )

    if (Test-Port $Port) {
        Write-Host "$Name already listening on port $Port"
        return
    }

    $stdout = Join-Path $runDir "$Name.stdout.log"
    $stderr = Join-Path $runDir "$Name.stderr.log"
    $process = Start-Process `
        -FilePath $FilePath `
        -ArgumentList $ArgumentList `
        -WorkingDirectory $WorkingDirectory `
        -WindowStyle Hidden `
        -RedirectStandardOutput $stdout `
        -RedirectStandardError $stderr `
        -PassThru
    Wait-Port -Name $Name -Port $Port -Process $process
    Start-Sleep -Seconds 1
    if ($process.HasExited) {
        throw "$Name exited during startup. Check $stderr."
    }

    $listenerPid = Get-ListeningProcessId -Port $Port
    if (-not $listenerPid) {
        throw "$Name is ready, but its listener process could not be identified."
    }

    @{
        name = $Name
        port = $Port
        launcherPid = $process.Id
        listenerPid = $listenerPid
        startedAt = (Get-Date).ToUniversalTime().ToString("o")
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runDir "$Name.process.json") -Encoding UTF8
}

$clamRoot = Join-Path $DataRoot "CLAM"
$checkpointDir = Join-Path $DataRoot "models\clam\camelyon_40x_cv\camelyon_40x_cv_CLAM_100_s1"
$resnetWeights = Join-Path $env:USERPROFILE ".cache\torch\hub\checkpoints\resnet50-19c8e357.pth"

$requiredPaths = @($clamRoot, $checkpointDir, $resnetWeights, $SamCheckpoint)
foreach ($path in $requiredPaths) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Required AI resource not found: $path"
    }
}

$env:AI_ANALYSIS_ROOT = Join-Path $DataRoot "runs\service-ai"
$env:CLAM_ROOT = $clamRoot
$env:CLAM_CHECKPOINT_DIR = $checkpointDir
$env:CLAM_RESNET_WEIGHTS = $resnetWeights
$env:CLAM_DEVICE = "cuda"
$env:CLAM_BATCH_SIZE = "64"
$env:CLAM_MAX_WORKERS = "1"
$env:SAM_CHECKPOINT = (Resolve-Path $SamCheckpoint).Path
$env:SAM_MODEL_TYPE = $SamModelType

$conda = (Get-Command conda.exe -ErrorAction Stop).Source
$condaEnvironments = @(
    & $conda env list 2>$null |
        ForEach-Object {
            $line = $_.ToString().Trim()
            if ($line -and $line -notmatch '^#') {
                ($line -split '\s+')[0]
            }
        }
)
if ($condaEnvironments -notcontains $CondaEnvironment) {
    if ($CondaEnvironment -eq "parvisight-py" -and $condaEnvironments -contains "pathorchestra-py") {
        Write-Warning "Conda environment parvisight-py is not installed; using legacy pathorchestra-py."
        $CondaEnvironment = "pathorchestra-py"
    } else {
        throw "Conda environment not found: $CondaEnvironment"
    }
}
Start-LocalProcess `
    -Name "python-service" `
    -Port 8000 `
    -FilePath $conda `
    -ArgumentList @("run", "--no-capture-output", "-n", $CondaEnvironment, "python", "-m", "uvicorn", "main:app", "--host", "127.0.0.1", "--port", "8000") `
    -WorkingDirectory (Join-Path $repoRoot "python-service")

$env:PARVISIGHT_LLM_ENABLED = "false"
if (-not $DisableLlm) {
    if (-not $RelayConfigPath -or -not (Test-Path -LiteralPath $RelayConfigPath)) {
        throw "Relay configuration not found: $RelayConfigPath"
    }

    $relay = @{}
    foreach ($line in Get-Content -LiteralPath $RelayConfigPath) {
        $trimmed = $line.Trim()
        if (-not $trimmed) { continue }
        $separator = $trimmed.IndexOf(":")
        if ($separator -lt 1) { continue }
        $name = $trimmed.Substring(0, $separator).Trim().ToLowerInvariant()
        $relay[$name] = $trimmed.Substring($separator + 1).Trim()
    }

    if (-not $relay["base url"] -or -not $relay["api key"] -or -not $relay["model"]) {
        throw "Relay configuration must define Base URL, api key, and model."
    }

    $env:PARVISIGHT_LLM_ENABLED = "true"
    $env:PARVISIGHT_LLM_BASE_URL = $relay["base url"]
    $env:PARVISIGHT_LLM_API_KEY = $relay["api key"]
    $env:PARVISIGHT_LLM_MODEL = if ($LlmModelOverride) {
        $LlmModelOverride
    } else {
        $relay["model"]
    }
}

$maven = (Get-Command mvn.cmd -ErrorAction Stop).Source
Start-LocalProcess `
    -Name "backend" `
    -Port 8080 `
    -FilePath $maven `
    -ArgumentList @("spring-boot:run", "-Dspring-boot.run.arguments=--debug=false") `
    -WorkingDirectory (Join-Path $repoRoot "backend")

$npm = (Get-Command npm.cmd -ErrorAction Stop).Source
Start-LocalProcess `
    -Name "frontend" `
    -Port 5173 `
    -FilePath $npm `
    -ArgumentList @("run", "dev", "--", "--host", "127.0.0.1", "--port", "5173") `
    -WorkingDirectory (Join-Path $repoRoot "frontend")

Write-Host ""
Write-Host "PARVISIGHT local stack is ready:"
Write-Host "  Product: http://127.0.0.1:5173"
Write-Host "  API:     http://127.0.0.1:8080"
Write-Host "  AI:      http://127.0.0.1:8000/docs"
