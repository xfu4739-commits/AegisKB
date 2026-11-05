$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "_console-init.ps1") -WindowTitle "AegisKB | rag-tools :18080"
. (Join-Path $PSScriptRoot "_load-local-config.ps1")

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$ragRoot = Join-Path $RepoRoot "aegis-kb-rag-tools"
$python = Join-Path $ragRoot ".venv\Scripts\python.exe"

if (-not (Test-Path -LiteralPath $python)) {
    throw @(
        "Existing venv not found: $python",
        "This script never creates a new venv. One-time setup (only if missing):",
        "  cd aegis-kb-rag-tools",
        "  python -m venv .venv",
        "  .\.venv\Scripts\pip install -e ."
    ) -join [Environment]::NewLine
}

$env:PYTHONUNBUFFERED = "1"
$env:PYTHONIOENCODING = "utf-8"
Set-Location $ragRoot

Write-Host "Using venv: $python" -ForegroundColor DarkGray
Write-Host "Starting rag-tools on http://127.0.0.1:18080 (health: /health)" -ForegroundColor Green
Write-Host ""
& $python -m uvicorn rag_tools.main:app --host 0.0.0.0 --port 18080
