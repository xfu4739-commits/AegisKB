# Opens three PowerShell windows (Java, rag-tools, Vue).
# Prerequisite: docker compose up -d in repo root.
$ErrorActionPreference = "Stop"

$scriptsDir = $PSScriptRoot
$RepoRoot = (Resolve-Path (Join-Path $scriptsDir "..")).Path
$envFile = Join-Path $RepoRoot ".env"

Write-Host "Repo: $RepoRoot" -ForegroundColor DarkGray

if (-not (Test-Path -LiteralPath $envFile)) {
    $msg = "缺少 $envFile" + [Environment]::NewLine + "请复制 .env.example 为 .env 并填写密钥后再启动。"
    throw $msg
}

try {
    . (Join-Path $scriptsDir "_load-local-config.ps1")
} catch {
    throw "加载 .env 失败: $_"
}

$dockerOk = $false
try {
    $ps = docker compose -f (Join-Path $RepoRoot "docker-compose.yaml") ps --status running 2>$null
    if ($ps -match "aegis-kb-mysql") { $dockerOk = $true }
} catch {
    $dockerOk = $false
}

if (-not $dockerOk) {
    Write-Host ""
    Write-Host "警告: 未检测到运行中的 Docker 中间件 (aegis-kb-mysql)。" -ForegroundColor Yellow
    Write-Host "请先在仓库根目录执行: docker compose up -d" -ForegroundColor Yellow
    Write-Host ""
}

Write-Host ""
Write-Host "将依次打开 3 个窗口（请看窗口标题栏区分）:" -ForegroundColor Cyan
Write-Host "  AegisKB | Java 后端 :9083" -ForegroundColor Cyan
Write-Host "  AegisKB | rag-tools :18080" -ForegroundColor Cyan
Write-Host "  AegisKB | Vue 前端 :5173  （独立窗口内会等 Java 就绪后再启动 Vite）" -ForegroundColor Cyan
Write-Host ""

function Start-ServiceWindow {
    param([string]$ScriptName)
    $path = (Resolve-Path (Join-Path $scriptsDir $ScriptName)).Path
    Start-Process powershell.exe -WorkingDirectory $RepoRoot -ArgumentList @(
        "-NoExit"
        "-ExecutionPolicy", "Bypass"
        "-NoProfile"
        "-File", $path
    ) | Out-Null
}

Start-ServiceWindow "start-java.ps1"
Write-Host "已打开 Java 窗口" -ForegroundColor Green
Start-Sleep -Seconds 3

Start-ServiceWindow "start-rag.ps1"
Write-Host "已打开 rag-tools 窗口" -ForegroundColor Green
Start-Sleep -Seconds 2

Start-ServiceWindow "start-vue.ps1"
Write-Host "已打开 Vue 窗口（内部轮询 9083，本 Cursor/终端窗口不会一直刷等待）" -ForegroundColor Green

Write-Host ""
Write-Host "说明: 下面三个服务在独立 PowerShell 窗口里运行；本窗口只负责拉起它们。" -ForegroundColor DarkGray
Write-Host "浏览器: http://localhost:5173/" -ForegroundColor Green
Read-Host "按 Enter 关闭本窗口（不会停止那三个服务窗口）"
