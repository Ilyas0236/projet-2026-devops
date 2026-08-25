# ☁️ Déploiement Azure — Wydad Digital

> Journal complet du déploiement effectué le **2026-08-24** sur le compte étudiant Azure, + guide de gestion du crédit de 100 $.

---

## 1. Ce qu'on a fait (étape par étape)

### 1.1 Compte & connexion
- Compte : **Azure for Students** (100 $ de crédit, renouvelable chaque année d'études)
- Connexion CLI : `az login --tenant f93d5f40-88c0-4650-b8f2-cc4ec3ef6a10`
  - ⚠️ Le MFA expire régulièrement → si erreur `AADSTS50078`, refaire cette commande
- Abonnement actif : `Azure for Students` — ID `11486e2c-efc6-4bf1-ac27-77e3a03734bf`

### 1.2 Contraintes découvertes (important pour la suite)
| Constat | Détail |
|---|---|
| Policy régions | L'abonnement étudiant n'autorise que **5 régions** : `italynorth`, `germanywestcentral`, `swedencentral`, `polandcentral`, `spaincentral` |
| Capacité B2s | Le SKU classique `Standard_B2s` est **indisponible** dans ces régions pour ce compte → on a pris l'équivalent moderne |
| Solution retenue | **Standard_B2s_v2** — 2 vCPU / **8 Go RAM**, ~30 $/mois, disponible en Spain Central |

> Commandes utiles si un jour il faut changer :
> ```bash
> # voir les régions autorisées
> az policy assignment list --disable-scope-strict-match --query "[].displayName" -o table
> az policy assignment list --disable-scope-strict-match --query "[?displayName=='Allowed resource deployment regions'].parameters.listOfAllowedLocations.value" -o json
> ```

### 1.3 Création des ressources (CLI)
```bash
az group create --name wydad-rg --location spaincentral

az vm create \
  --resource-group wydad-rg \
  --name wydad-vm \
  --location spaincentral \
  --image Canonical:ubuntu-24_04-lts:server:latest \
  --size Standard_B2s_v2 \
  --admin-username azureuser \
  --ssh-key-values "@$env:USERPROFILE\.ssh\id_ed25519.pub" \
  --public-ip-sku Standard \
  --os-disk-size-gb 64
```
- **IP publique** : `158.158.74.169`
- Utilisateur SSH : `azureuser` (connexion par clé `id_ed25519`, pas de mot de passe)

Ports ouverts dans le NSG (`wydad-vmNSG`) :
```bash
az vm open-port --resource-group wydad-rg --name wydad-vm --port 80  --priority 1001
az vm open-port --resource-group wydad-rg --name wydad-vm --port 443 --priority 1002
```
Règles finales : 22 (SSH) + 80 (HTTP) + 443 (HTTPS).

### 1.4 Logiciel installé sur la VM
- **Docker Engine 29 + Docker Compose v5** via le script officiel :
  ```bash
  curl -fsSL https://get.docker.com | sudo sh
  sudo usermod -aG docker azureuser   # docker sans sudo (reconnexion nécessaire)
  ```
- **OpenJDK 21 + Maven 3.8** (nécessaires car les Dockerfiles copient des jars précompilés) :
  ```bash
  sudo apt install openjdk-21-jdk-headless maven
  ```

### 1.5 Application déployée
```bash
git clone https://github.com/Ilyas0236/projet-2026-devops.git wydad-digital-parent
cd wydad-digital-parent
```

Fichier `.env` créé à la racine (**hors git**, chmod 600) avec secrets générés par `openssl rand` :
```
POSTGRES_USER, POSTGRES_PASSWORD, JWT_SECRET,
ADMIN_SEED_PASSWORD, INTERNAL_SECRET,
GATEWAY_HOST_PORT=8080, FRONTEND_HOST_PORT=4200
```

Lancement :
```bash
mvn -q -T 1C package -DskipTests
docker compose up -d --build
```

---

## 2. 🔐 Identifiants & accès

| Quoi | Valeur |
|---|---|
| SSH | `ssh azureuser@158.158.74.169` (clé privée locale, ne JAMAIS partager) |
| Frontend (dev) | http://158.158.74.169 |
| API Gateway (dev) | http://158.158.74.169:8080 |
| Admin seed login | `admin@wac.ma` |
| Admin seed password | dans le `.env` serveur (chmod 600) et ton gestionnaire de mots de passe — **jamais dans git** ; à changer avant l'ouverture publique ⚠️ |
| `.env` serveur | `/home/azureuser/wydad-digital-parent/.env` (chmod 600) |

> Règle absolue : les secrets vivent uniquement sur le serveur et dans ton gestionnaire de mots de passe — jamais dans git ni dans un chat.

---

## 3. 🔁 Boucle de travail dev → deploy (au fil de l'eau)

```bash
# ── En local (Windows) ──
git add . && git commit -m "feat: ..." && git push

# ── Sur le serveur ──
ssh azureuser@158.158.74.169
cd wydad-digital-parent
git pull
mvn -q -T 1C package -DskipTests     # ~2-3 min (cache ~/.m2 après le premier build)
docker compose up -d --build         # reconstruit uniquement les services modifiés
docker compose ps                    # vérifier que tout est healthy/running
docker compose logs -f api-gateway   # en cas de problème
```

Améliorations possibles plus tard (optionnel) :
- Script `deploy.ps1` local qui enchaîne push + ssh pull/build automatiquement
- Dockerfiles multi-stage (build Maven dans l'image) → plus besoin de Maven sur la VM
- Caddy reverse proxy + HTTPS dès qu'on a un domaine
- GitHub Actions qui déploie tout seul à chaque push sur `main`

---

## 4. 💰 Gestion du crédit de 100 $

### 4.1 Coûts réels actuels

| Ressource | Prix approximatif |
|---|---|
| VM **Standard_B2s_v2** (2 vCPU / 8 Go) tournée 24/7 | ~30 $/mois (~1,10 $/jour) |
| IP publique Standard | ~3 $/mois |
| Disque 64 Go (P6 SSD géré) | ~5-7 $/mois |
| Bande passante sortante (faible usage) | ~0 $ (les 100 Go/mois gratuits suffisent) |
| **Total** | **~38-40 $/mois** → crédit épuisé en **~2,5 mois** si la VM tourne 24/7 |

### 4.2 🎯 La règle d'or : DEALLOCATE quand tu ne t'en sers pas

Une VM simplement « arrêtée » depuis Windows continue d'être **facturée**. Il faut la **désallouer** (deallocate) :

```powershell
# Arrêter VRAIMENT la VM (plus facturée — seul le disque reste payé, quelques centimes/jour)
az vm deallocate --resource-group wydad-rg --name wydad-vm

# Redémarrer
az vm start --resource-group wydad-rg --name wydad-vm

# Redémarrer (soft reboot)
az vm restart --resource-group wydad-rg --name wydad-vm
```

⚠️ Après chaque deallocate/start, **l'IP publique peut rester la même** (SKU Standard la conserve tant que la ressource existe), mais vérifie avec :
```powershell
az vm show -d --resource-group wydad-rg --name wydad-vm --query publicIps -o tsv
```

### 4.3 Stratégie recommandée pendant tes études/projet

| Usage | Action | Coût |
|---|---|---|
| Semaine de développement actif | VM allumée jour et nuit | ~1,30 $/jour |
| Tu travailles seulement le soir | Deallocate le matin, start le soir | ~0,60 $/jour (disque + IP) |
| Vacances / période sans dev | Deallocate prolongé | ~0,15 $/jour |
| Fin de semestre, projet terminé mais à montrer | Allumer la VM 1h avant la démo | quasi 0 |

Avec cette discipline, les 100 $ peuvent tenir **4-6 mois**.

💡 Astuce bonus : les comptes étudiants ont aussi **750 h/mois gratuites sur B1s** pendant 12 mois... mais B1s (1 Go RAM) est trop juste pour nos 9 services Java — on préfère payer la B2s_v2 avec le crédit.

### 4.4 Surveiller ta consommation

```powershell
# Crédit restant (via Cost Management)
az consumption usage list --top 5 --query "[].{date:usageStart, cost:pretaxCost, service:meterDetails.meterCategory}" -o table

# Ou dans le portail : https://portal.azure.com → Cost Management + Billing → Azure for Students
```

Le portail affiche directement **« Solde restant »** sur la page du sponsoring étudiant — va y jeter un œil une fois par semaine.

🚨 Suggestion : créer une **alerte budget** dans le portail (Cost Management → Budgets → Nouveau budget, seuil 50 $ puis 80 $) pour recevoir un email avant de manquer de crédit.

### 4.5 Si jamais le crédit arrive à zéro
- Azure for Students **ne prélève jamais** sur une carte : la VM se met juste en pause
- Options alors : renouveler le compte (si toujours étudiant, nouveau crédit chaque année), passer à un VPS à 5 €/mois (Hetzner/OVH — même procédure Docker, cf. ROADMAP-DEPLOIEMENT.md §4.1)

---

## 5. Checklist post-premier-lancement (à faire une fois l'app démarrée)

- [ ] Ouvrir http://158.158.74.169 → page d'accueil visible
- [ ] Se connecter avec le compte admin seed → **changer le mot de passe immédiatement**
- [ ] Tester le parcours public : actualités, inscription visiteur
- [ ] Vérifier les logs : `docker compose logs -f api-gateway auth-service`
- [ ] Mettre en place la sauvegarde Postgres (cron quotidien `pg_dumpall`, cf. ROADMAP §4.6)
- [ ] Créer une alerte budget 50 $/80 $ dans le portail Azure

---

*Généré le 2026-08-24 — Wydad Digital · déploiement initial Azure*

---

## 6. Journal — 25/08/2026 : audit thématique + correctifs de routage public

### Ce qui a été déployé
- **Nouveaux services en prod** : `election-service` (:8089, élections président + sondages) et `communication-service` (:8090, messagerie privée + annonces staff + chat de groupe STOMP) → **15 conteneurs healthy**.
- **Migrations thématiques** : sondages sortis de sports-service → election-service ; messaging/team-chat sortis de sports-service → communication-service (routes gateway `/api/sports/messaging|team-chat` conservées pour compatibilité front).
- **Correctifs du jour** :
  1. Surefire épinglé 3.2.5 + compiler 3.13.0 dans le parent pom — Maven ≤3.8 exécutait silencieusement **0 test** sur la VM.
  2. `elections_db` / `communication_db` créées manuellement via psql (volume Postgres antérieur aux init-scripts) ; init-script mis à jour pour les futurs environnements.
  3. Clé compose dupliquée (`networks.name`) supprimée.
  4. Gateway : lecture publique `/api/polls/active` + `/api/elections/published/**` (exigence B.8) puis `/api/shop/products*` + `/api/ticket/events*` (commit 5d69380) — les services avaient déjà `permitAll`, c'est le filtre JWT global qui bloquait.

### Preuves sur la VM (après rebuild gateway)
```
api/polls/active                 200   (public)
api/elections/published/latest   204   (public, vide)
api/shop/products                200   (public)
api/ticket/events                200   (public)
api/sports/messaging/inbox       401   (JWT requis)
```
Tests gateway : 5/5 verts local (`InternalRoutesBlockedTest` 3 + `PublicCatalogAccessTest` 2).

### Reste à faire côté serveur
- [ ] Installer le cron de sauvegarde : `bash scripts/install-backup-cron.sh`
- [ ] Avant ouverture publique : changer mot de passe seed admin, régénérer secret LiveKit + clé Cloudinary
