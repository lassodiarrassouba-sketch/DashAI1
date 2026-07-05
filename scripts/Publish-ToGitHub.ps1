param(
    [Parameter(Mandatory = $true)]
    [string] $RepositoryUrl,

    [string] $CommitMessage = "Prepare DashAI deployment",
    [string] $GitUserName = "DashAI Publisher",
    [string] $GitUserEmail = "lassodiarrassouba@gmail.com"
)

$ErrorActionPreference = "Stop"

function Resolve-ProjectRoot {
    $scriptDir = Split-Path -Parent $MyInvocation.ScriptName
    return (Resolve-Path (Join-Path $scriptDir "..")).Path
}

function Get-GitCommand {
    $git = Get-Command git -ErrorAction SilentlyContinue
    if ($git) {
        return $git.Source
    }

    $bundledGit = "C:\Users\msi\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\git\cmd\git.exe"
    if (Test-Path -LiteralPath $bundledGit) {
        return $bundledGit
    }

    throw "Git est introuvable. Installe Git ou lance ce script depuis un environnement qui fournit git.exe."
}

function Assert-SafeToPublish {
    $blockedPaths = @(
        "server\.env",
        "android\keystore.properties",
        "android\keys\dashai-release.jks"
    )

    foreach ($path in $blockedPaths) {
        if (Test-Path -LiteralPath $path) {
            Write-Output "Secret local detecte et garde hors Git : $path"
        }
    }

    $requiredIgnoreRules = @("*.apk", "*.aab", ".env", "keystore.properties", "*.jks")
    $gitignore = Get-Content -LiteralPath ".gitignore" -Raw
    foreach ($rule in $requiredIgnoreRules) {
        if ($gitignore -notmatch [regex]::Escape($rule)) {
            throw "Regle manquante dans .gitignore : $rule"
        }
    }
}

$projectRoot = Resolve-ProjectRoot
Set-Location $projectRoot

if ($RepositoryUrl -notmatch "^https://github\.com/[^/]+/[^/]+(\.git)?$") {
    throw "RepositoryUrl doit ressembler a https://github.com/UTILISATEUR/dashai.git"
}

$git = Get-GitCommand

function Invoke-Git {
    param(
        [Parameter(ValueFromRemainingArguments = $true)]
        [string[]] $GitArgs
    )

    & $git @GitArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Commande Git echouee : git $($GitArgs -join ' ')"
    }
}

function Invoke-GitOutput {
    param(
        [Parameter(ValueFromRemainingArguments = $true)]
        [string[]] $GitArgs
    )

    $output = & $git @GitArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Commande Git echouee : git $($GitArgs -join ' ')"
    }
    return $output
}

function Test-GitRepository {
    & $git rev-parse --is-inside-work-tree *> $null
    return $LASTEXITCODE -eq 0
}

Assert-SafeToPublish

if (!(Test-GitRepository)) {
    Invoke-Git init
}

Invoke-Git config user.name $GitUserName
Invoke-Git config user.email $GitUserEmail
Invoke-Git branch -M main

$remoteExists = $false
& $git remote get-url origin *> $null
if ($LASTEXITCODE -eq 0) {
    $remoteExists = $true
}

if ($remoteExists) {
    Invoke-Git remote set-url origin $RepositoryUrl
} else {
    Invoke-Git remote add origin $RepositoryUrl
}

$statusBeforeAdd = Invoke-GitOutput status --short
Invoke-Git add .

$status = Invoke-GitOutput status --short
if ([string]::IsNullOrWhiteSpace(($status | Out-String))) {
    Write-Output "Aucun changement a publier."
} else {
    Invoke-Git commit -m $CommitMessage
}

Invoke-Git push -u origin main

Write-Output "Projet DashAI publie sur : $RepositoryUrl"
