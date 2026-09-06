# deploy-prod.ps1 — Déclenche le redéploiement de la stack sur la VM prod
# Usage : .\deploy-prod.ps1
#
# Ce script fait exactement ce que fait manuellement DEPLOIEMENT-AZURE.md §3 :
#   1. ssh azureuser@158.158.74.169
#   2. cd wydad-digital-parent
#   3. git pull
#   4. mvn package
#   5. docker compose pull + up -d
#
# Prérequis : clé SSH chargée dans ssh-agent (ssh-add ~/.ssh/id_ed25519)
# ou passée via -KeyPath. La VM doit être joignable.

param(
    [string]$Host = "158.158.74.169",
    [string]$User = "azureuser",
    [string]$KeyPath = "$env:USERPROFILE\.ssh\id_ed25519",
    [string]$RepoDir = "wydad-digital-parent"
)

$ErrorActionPreference = "Stop"
$startTime = Get-Date

Write-Host "=== DEPLOIEMENT PROD $Host ===" -ForegroundColor Cyan
Write-Host "User  : $User"
Write-Host "Key   : $KeyPath"
Write-Host "Repo  : ~/$RepoDir"
Write-Host ""

if (-not (Test-Path $KeyPath)) {
    Write-Error "Clé SSH introuvable : $KeyPath. Génère-la avec : ssh-keygen -t ed25519"
}

# Sanity check : la VM est-elle joignable ?
Write-Host "[1/4] Test connectivité SSH..." -ForegroundColor Yellow
ssh -i $KeyPath -o StrictHostKeyChecking=accept-new -o ConnectTimeout=5 `
    "$User@$Host" "echo OK_\$(date +%s)" 2>&1 | Tee-Object -Variable sshOut | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Error "Connexion SSH impossible (exit $LASTEXITCODE). Vérifie la clé et le NSG."
}
Write-Host "    Connecté. $sshOut"

# Pull + rebuild + restart
Write-Host "[2/4] git pull sur la VM..." -ForegroundColor Yellow
ssh -i $KeyPath "$User@$Host" "cd ~/$RepoDir && git pull" 2>&1 | Out-Host
if ($LASTEXITCODE -ne 0) { Write-Error "git pull a échoué (exit $LASTEXITCODE)" }

Write-Host "[3/4] mvn package (reconstruit les jars, ~2-3 min)..." -ForegroundColor Yellow
ssh -i $KeyPath "$User@$Host" "cd ~/$RepoDir && mvn -q -T 1C package -DskipTests" 2>&1 | Out-Host
if ($LASTEXITCODE -ne 0) { Write-Error "mvn package a échoué (exit $LASTEXITCODE)" }

Write-Host "[4/4] docker compose pull + up -d..." -ForegroundColor Yellow
ssh -i $KeyPath "$User@$Host" "cd ~/$RepoDir && docker compose pull && docker compose up -d --remove-orphans" 2>&1 | Out-Host
if ($LASTEXITCODE -ne 0) { Write-Error "docker compose a échoué (exit $LASTEXITCODE)" }

# Health check
Write-Host ""
Write-Host "[health] /actuator/health via gateway..." -ForegroundColor Yellow
$health = Invoke-RestMethod -Uri "http://$Host`:8080/actuator/health" -TimeoutSec 10
if ($health.status -eq "UP") {
    Write-Host "    ✅ Gateway UP" -ForegroundColor Green
} else {
    Write-Warning "    ⚠️ Gateway status=$($health.status)"
}

$elapsed = (Get-Date) - $startTime
Write-Host ""
Write-Host "=== DEPLOIEMENT TERMINE en $($elapsed.ToString('mm\:ss')) ===" -ForegroundColor Green
Write-Host "Test panier 0 DH : http://$Host/panier (login ali@gmail.com / alidinar)"
