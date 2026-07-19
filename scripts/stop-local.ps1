[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$runDir = Join-Path $repoRoot ".run"

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

foreach ($name in "frontend", "backend", "python-service") {
    $metadataPath = Join-Path $runDir "$name.process.json"
    if (-not (Test-Path -LiteralPath $metadataPath)) {
        Write-Host "No tracked $name process"
        continue
    }

    $metadata = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
    $listenerPid = Get-ListeningProcessId -Port ([int]$metadata.port)
    if ($listenerPid -and $listenerPid -eq [int]$metadata.listenerPid) {
        $process = Get-Process -Id $listenerPid -ErrorAction SilentlyContinue
        if ($process) {
            Stop-Process -Id $listenerPid -Force
            Write-Host "Stopped $name on port $($metadata.port)"
        }
    } elseif ($listenerPid) {
        Write-Warning "Port $($metadata.port) belongs to an untracked process; it was not stopped."
    } else {
        Write-Host "$name is not listening on port $($metadata.port)"
    }

    Remove-Item -LiteralPath $metadataPath -Force
}
