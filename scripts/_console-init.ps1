param(
    [Parameter(Mandatory = $true)]
    [string]$WindowTitle
)

$Host.UI.RawUI.WindowTitle = $WindowTitle

try {
    $null = chcp.com 65001
} catch {
    # ignore
}

$utf8 = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = $utf8
[Console]::InputEncoding = $utf8
$OutputEncoding = $utf8

Write-Host ""
Write-Host "========================================" -ForegroundColor DarkCyan
Write-Host " $WindowTitle" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor DarkCyan
Write-Host ""
