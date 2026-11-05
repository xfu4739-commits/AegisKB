function Test-AegisKbBackendHealth {
    param(
        [string]$Url = "http://127.0.0.1:9083/actuator/health"
    )
    try {
        if (Get-Command curl.exe -ErrorAction SilentlyContinue) {
            $content = & curl.exe -s -S --max-time 8 $Url 2>$null
            if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($content)) {
                return $false
            }
            return ($content -match '"status"\s*:\s*"UP"')
        }

        $wc = New-Object System.Net.WebClient
        $wc.Headers.Add("Accept", "application/json")
        $content = $wc.DownloadString($Url)
        return ($content -match '"status"\s*:\s*"UP"')
    }
    catch {
        return $false
    }
}

function Wait-AegisKbBackendHealth {
    param(
        [string]$Url = "http://127.0.0.1:9083/actuator/health",
        [int]$TimeoutSec = 600
    )
    $started = Get-Date
    $deadline = $started.AddSeconds($TimeoutSec)
    $tick = 0
    while ((Get-Date) -lt $deadline) {
        if (Test-AegisKbBackendHealth -Url $Url) {
            return
        }
        $tick++
        if ($tick -eq 1 -or ($tick % 5) -eq 0) {
            $elapsed = [int](((Get-Date) - $started).TotalSeconds)
            Write-Host "等待 Java 9083 就绪... 已等待 ${elapsed}s（约每 15s 提示一次；请看 Java 窗口是否已出现 Started）" -ForegroundColor Yellow
        }
        Start-Sleep -Seconds 3
    }
    $msg = "超时: ${TimeoutSec}s 内 9083 仍未就绪。" + [Environment]::NewLine +
        "请查看「AegisKB | Java 后端 :9083」窗口；浏览器可试 http://127.0.0.1:9083/actuator/health"
    throw $msg
}
