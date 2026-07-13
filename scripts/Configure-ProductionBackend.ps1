param(
    [Parameter(Mandatory = $true)]
    [string] $BackendUrl,

    [switch] $BuildApk
)

$ErrorActionPreference = "Stop"

function Resolve-ProjectRoot {
    $scriptDir = Split-Path -Parent $MyInvocation.ScriptName
    return (Resolve-Path (Join-Path $scriptDir "..")).Path
}

function Normalize-DashAiEndpoint {
    param([string] $RawUrl)

    $clean = $RawUrl.Trim()
    if ([string]::IsNullOrWhiteSpace($clean)) {
        throw "BackendUrl est vide."
    }

    $clean = $clean.TrimEnd("/")
    if ($clean -notmatch "^https://") {
        throw "La release Android doit utiliser HTTPS. Recu : $clean"
    }

    if ($clean -match "/api/ask$") {
        return $clean
    }
    if ($clean -match "/api$") {
        return "$clean/ask"
    }
    return "$clean/api/ask"
}

function Set-PropertyValue {
    param(
        [string] $Path,
        [string] $Key,
        [string] $Value
    )

    if (!(Test-Path -LiteralPath $Path)) {
        throw "Fichier introuvable : $Path"
    }

    $lines = Get-Content -LiteralPath $Path
    $found = $false
    $updated = foreach ($line in $lines) {
        if ($line -match "^$([regex]::Escape($Key))=") {
            $found = $true
            "$Key=$Value"
        } else {
            $line
        }
    }

    if (!$found) {
        $updated += "$Key=$Value"
    }

    Set-Content -LiteralPath $Path -Value $updated -Encoding ASCII
}

$projectRoot = Resolve-ProjectRoot
$endpoint = Normalize-DashAiEndpoint -RawUrl $BackendUrl

$keystoreProperties = Join-Path $projectRoot "android\keystore.properties"
$webConfig = Join-Path $projectRoot "web\config.js"
$androidDir = Join-Path $projectRoot "android"
$releaseApk = Join-Path $androidDir "app\build\outputs\apk\release\app-release.apk"
$downloadApk = Join-Path $projectRoot "download-site\dashai-1.0.4.apk"
$downloadZip = Join-Path $projectRoot "dashai-android-download-netlify.zip"

Set-PropertyValue -Path $keystoreProperties -Key "DASHAI_PROD_API_ENDPOINT" -Value $endpoint

if (Test-Path -LiteralPath $webConfig) {
    $configText = Get-Content -LiteralPath $webConfig -Raw
    $replacement = "defaultBackendUrl: `"$endpoint`""
    $configText = [regex]::Replace($configText, 'defaultBackendUrl:\s*"[^"]*"', $replacement)
    Set-Content -LiteralPath $webConfig -Value $configText -Encoding ASCII
}

Write-Output "Endpoint production configure : $endpoint"

if ($BuildApk) {
    Push-Location $androidDir
    try {
        .\gradlew.bat :app:assembleRelease
        if ($LASTEXITCODE -ne 0) {
            throw "La build Gradle release a echoue."
        }
    } finally {
        Pop-Location
    }

    if (!(Test-Path -LiteralPath $releaseApk)) {
        throw "APK release introuvable apres build : $releaseApk"
    }

    Get-ChildItem -LiteralPath (Join-Path $projectRoot "download-site") -Filter "dashai-*.apk" |
        Remove-Item -Force
    Copy-Item -LiteralPath $releaseApk -Destination $downloadApk -Force
    Compress-Archive -Path (Join-Path $projectRoot "download-site\*") -DestinationPath $downloadZip -Force
    Write-Output "APK mise a jour : $downloadApk"
    Write-Output "Archive Netlify mise a jour : $downloadZip"
}
