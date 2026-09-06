# CI/CD — Auto-déploiement prod

## État
- ✅ Job `deploy-prod` ajouté dans `.github/workflows/ci.yml`
- ✅ Skip automatique si les 3 secrets ne sont pas configurés
- ✅ Script `deploy-prod.ps1` créé pour déploiement manuel Windows

## Pour activer l'auto-déploiement (3 secrets à ajouter)
Allez sur https://github.com/Ilyas0236/projet-2026-devops/settings/secrets/actions

1. **PROD_VM_HOST** = `158.158.74.169`
2. **PROD_VM_USER** = `azureuser`
3. **PROD_VM_SSH_KEY** = contenu de `~/.ssh/id_ed25519` (clé privée PEM sur 1 ligne)

Une fois ajoutés, chaque push sur `main` :
1. Build les jars (cache Maven)
2. Build + push les images Docker
3. SSH sur la VM → `git pull` + `mvn package` + `docker compose pull && up -d`

## Pour le faire UNE fois maintenant (sans configurer les secrets)
```powershell
.\deploy-prod.ps1
```

Prérequis : clé SSH dans `~/.ssh/id_ed25519` (et NSG port 22 ouvert).
