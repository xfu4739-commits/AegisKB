$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "_console-init.ps1") -WindowTitle "AegisKB | Java 后端 :9083"
. (Join-Path $PSScriptRoot "_load-local-config.ps1")

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $RepoRoot

$jar = Join-Path $RepoRoot "aegis-kb-business\aegis-kb-business-chat\target\aegis-kb-business-chat-0.0.1-SNAPSHOT.jar"

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw "找不到 java 命令。请安装 JDK 17+ 或将 JAVA_HOME\bin 加入 PATH。"
}

try {
    $javaVersion = & java -version 2>&1 | Out-String
    if ($javaVersion -match 'version "(\d+)') {
        $major = [int]$matches[1]
        if ($major -gt 21) {
            Write-Host "提示: 当前 JDK $major，项目按 Java 17 编译。若启动异常，请改用 JDK 17 或 21。" -ForegroundColor Yellow
        }
    }
} catch {
    # ignore
}

. (Join-Path $PSScriptRoot "_wait-backend-health.ps1")

function Test-AegisKbBackendUp {
    Test-AegisKbBackendHealth
}

$portInUse = Get-NetTCPConnection -LocalPort 9083 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($portInUse) {
    if (Test-AegisKbBackendUp) {
        Write-Host "9083 上已有运行中的 Java 后端，无需重复启动本窗口。" -ForegroundColor Green
        Read-Host "按 Enter 关闭本窗口"
        exit 0
    }
    Write-Host "错误: 9083 端口已被占用，但 /actuator/health 未返回 UP。" -ForegroundColor Red
    Write-Host "请执行 netstat -ano | findstr :9083 找到 PID 并结束进程后重试。" -ForegroundColor Yellow
    Read-Host "按 Enter 关闭本窗口"
    exit 1
}

if (-not (Test-Path -LiteralPath $jar)) {
    if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
        throw "找不到 jar 且 PATH 中没有 mvn。请先在本机打包或安装 Maven。"
    }
    Write-Host "未找到 jar，将执行 Maven 打包（首次可能 5~15 分钟，请耐心等待）..." -ForegroundColor Yellow
    mvn -pl aegis-kb-business/aegis-kb-business-chat -am package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Maven 打包失败，退出码 $LASTEXITCODE" -ForegroundColor Red
        Read-Host "按 Enter 关闭本窗口"
        exit $LASTEXITCODE
    }
}

$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
# 本地启动：避免 Hikari 一次拉起过多 MySQL 连接导致长时间无日志；降低 Kafka 刷屏
$env:SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE = "10"
$env:SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE = "50"
$env:LOGGING_LEVEL_ORG_APACHE_KAFKA = "WARN"
$env:LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_AI = "INFO"

Write-Host "Starting Java on http://127.0.0.1:9083 (health: /actuator/health)" -ForegroundColor Green
Write-Host "启动完成后日志可能停在 MinIO/Kafka 附近不再滚动——这是正常的，进程在等请求。" -ForegroundColor DarkGray
Write-Host "请勿用鼠标在黑色窗口里框选文字（QuickEdit 会暂停 Java，看起来像卡死）。" -ForegroundColor Yellow
Write-Host "Kafka/MinIO 日志后通常会出现 Started AegisKbApplication；也可浏览器打开 health 链接确认 UP。" -ForegroundColor DarkGray
Write-Host "本窗口标题栏应显示: AegisKB | Java 后端 :9083" -ForegroundColor DarkGray
Write-Host ""
java -jar $jar
