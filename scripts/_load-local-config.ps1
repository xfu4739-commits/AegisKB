# Loads repo-root `.env` into the current process (used by start-*.ps1).
$ErrorActionPreference = "Stop"

function Import-DotEnvFile {
    param([Parameter(Mandatory = $true)][string]$Path)

    Get-Content -LiteralPath $Path -Encoding UTF8 | ForEach-Object {
        $line = $_.Trim()
        if ($line -eq "" -or $line.StartsWith("#")) { return }

        if ($line -match '^\s*export\s+(.+)$') {
            $line = $matches[1].Trim()
        }

        $eq = $line.IndexOf("=")
        if ($eq -lt 1) { return }

        $name = $line.Substring(0, $eq).Trim()
        $value = $line.Substring($eq + 1).Trim()

        if (
            ($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'"))
        ) {
            $value = $value.Substring(1, $value.Length - 2)
        }

        Set-Item -Path "Env:$name" -Value $value
    }
}

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$EnvFile = Join-Path $RepoRoot ".env"

if (-not (Test-Path -LiteralPath $EnvFile)) {
    throw ('Missing local config: {0}{1}Copy .env.example to .env and set your API keys.' -f $EnvFile, [Environment]::NewLine)
}

Import-DotEnvFile -Path $EnvFile

if ([string]::IsNullOrWhiteSpace($env:ALI_BAI_LIAN_API_KEY)) {
    throw "ALI_BAI_LIAN_API_KEY is empty after loading .env."
}
