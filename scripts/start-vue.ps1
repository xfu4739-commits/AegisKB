$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "_console-init.ps1") -WindowTitle "AegisKB | Vue 前端 :5173"
. (Join-Path $PSScriptRoot "_wait-backend-health.ps1")

function Wait-BackendHealth {
    Wait-AegisKbBackendHealth -TimeoutSec 600
    Write-Host "Java 后端已就绪: http://127.0.0.1:9083/actuator/health" -ForegroundColor Green
}

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$vueRoot = Join-Path $RepoRoot "vue"

if (-not (Test-Path (Join-Path $vueRoot "node_modules"))) {
    throw "node_modules missing under vue/$([Environment]::NewLine)Run once: cd vue && npm install"
}

if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
    throw "找不到 npm 命令。请安装 Node.js 并将 npm 加入 PATH。"
}

Wait-BackendHealth

Set-Location $vueRoot

Write-Host "Starting frontend on http://localhost:5173/ (API proxy -> 9083)" -ForegroundColor Green
Write-Host "若仍报 ECONNREFUSED 9083，说明 Java 窗口已退出。" -ForegroundColor DarkGray
Write-Host ""
npm run dev
