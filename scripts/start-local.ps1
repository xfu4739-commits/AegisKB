# 一次拉起 AegisKB 本机三件套：Java 9083、rag-tools 18080、Vue 5174。
# 用法：在仓库根目录执行  .\scripts\start-local.ps1
#       或双击 scripts\start-local.cmd
# 密钥：复制 scripts\start-local.secrets.ps1.example 为 start-local.secrets.ps1 并粘贴 Key
# 中间件请自行 docker compose up -d（本脚本不再检查或启动 Docker）

param(
    [switch]$SkipInfra  # 已无效，保留以免旧命令行报错
)

$ErrorActionPreference = "Stop"

$SecretsFile = Join-Path $PSScriptRoot "start-local.secrets.ps1"
if (Test-Path -LiteralPath $SecretsFile) {
    Write-Host "已加载 start-local.secrets.ps1"
    . $SecretsFile
    foreach ($name in @("TAVILY_API_KEY", "ALIBABA_CLOUD_ACCESS_KEY_ID", "ALIBABA_CLOUD_ACCESS_KEY_SECRET")) {
        if ([string]::IsNullOrWhiteSpace((Get-Item -Path "Env:$name" -ErrorAction SilentlyContinue).Value)) {
            Remove-Item -Path "Env:$name" -ErrorAction SilentlyContinue
        }
    }
}

$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$VueDir = Join-Path $Root "vue"
$RagVenv = Join-Path $Root "aegis-kb-rag-tools\.venv\Scripts\python.exe"

$ChatJar = Join-Path $Root "aegis-kb-business\aegis-kb-business-chat\target\aegis-kb-business-chat-0.0.1-SNAPSHOT.jar"

function Test-CommandExists {
    param([string]$Name)
    return [bool](Get-Command $Name -ErrorAction SilentlyContinue)
}

function Ensure-Prerequisites {
    if (-not (Test-CommandExists "mvn")) {
        throw "找不到 mvn。请安装 Maven 或从 IDE 启动 Java。"
    }
    if (-not (Test-Path -LiteralPath $RagVenv)) {
        throw "找不到 $RagVenv，请先在 aegis-kb-rag-tools 创建 .venv 并 pip install -r requirements.txt。"
    }
    if (-not (Test-CommandExists "npm")) {
        throw "找不到 npm。请安装 Node.js。"
    }
    if (-not (Test-Path (Join-Path $VueDir "node_modules"))) {
        Write-Host "Vue 依赖未安装，正在 vue 目录执行 npm install ..."
        Push-Location $VueDir
        try {
            & npm install
            if ($LASTEXITCODE -ne 0) {
                throw "npm install 失败，退出码 $LASTEXITCODE"
            }
        } finally {
            Pop-Location
        }
    }
}

function Ensure-JavaBuild {
    if (Test-Path -LiteralPath $ChatJar) {
        return
    }
    Write-Host "正在编译 Java（首次约 1-2 分钟，Maven package -am）..."
    Push-Location $Root
    try {
        & mvn.cmd -pl aegis-kb-business/aegis-kb-business-chat -am package -DskipTests
        if ($LASTEXITCODE -ne 0) {
            throw "Maven package 失败，退出码 $LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }
    if (-not (Test-Path -LiteralPath $ChatJar)) {
        throw "编译完成但未找到 $ChatJar"
    }
    Write-Host "Java 编译完成。"
}

function Start-DevCombined {
    $javaCmd = Join-Path $PSScriptRoot "dev-java.cmd"
    $ragCmd = Join-Path $PSScriptRoot "dev-rag.cmd"
    $vueCmd = Join-Path $PSScriptRoot "dev-vue.cmd"

    $npx = Get-Command npx.cmd -ErrorAction SilentlyContinue
    if (-not $npx) {
        $npx = Get-Command npx -ErrorAction Stop
    }

    Write-Host ""
    Write-Host "单窗口启动：java(9083) + rag(18080) + vue(5174)"
    Write-Host "Java 使用已编译 jar 启动；改代码后需重新 mvn package 或删 target 后再启动"
    Write-Host "日志前缀 [java] [rag] [vue]；按 Ctrl+C 一次停止全部"
    Write-Host "浏览器: http://localhost:5174/"
    Write-Host ""

    Push-Location $Root
    try {
        & $npx.Source @(
            "--yes", "concurrently@9.1.2",
            "--kill-others-on-fail",
            "-n", "java,rag,vue",
            "-c", "blue,magenta,green",
            "--",
            "`"$javaCmd`"",
            "`"$ragCmd`"",
            "`"$vueCmd`""
        )
        if ($LASTEXITCODE -ne 0) {
            exit $LASTEXITCODE
        }
    } finally {
        Pop-Location
    }
}

if (-not $env:ALI_BAI_LIAN_API_KEY) {
    Write-Warning "未设置 ALI_BAI_LIAN_API_KEY，Java / rag-tools 调用模型会失败。"
}

Ensure-Prerequisites
Ensure-JavaBuild
Start-DevCombined
