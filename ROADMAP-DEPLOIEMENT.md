# 🗺️ Roadmap restante + Déploiement complet — Wydad Digital

> Document généré le 2026-08-24, mis à jour avec l'espace Président. Il fait le point sur **ce qui reste à développer** (fonctionnalités communauté validées) et **comment déployer toute l'application** gratuitement — **Oracle Cloud Free Tier** recommandé + **Cloudinary** pour les vidéos/photos.
>
> Contraintes : solutions **100 % gratuites**, déploiement **Docker-only** (pas de Kubernetes), qualité selon **ISTQB** avec tests automatisés.

---

## 1. Où on en est

### ✅ Déjà en place
- Architecture microservices complète : `api-gateway` (porte d'entrée unique), `auth-service`, `content-service`, `payment-service`, `shop-service`, `ticket-service`, `notification-service`, `sports-service`, `gamification-service`
- PostgreSQL (une base par service), Redis, pgAdmin
- Frontend Angular (nginx en conteneur), design system clair blanc/rouge en cours (Lot 1 layout committé)
- Rôles existants : `VISITEUR, ADHERENT, PARENT, JOUEUR, STAFF, ADMIN`
- Paiements (Stripe), boutique, billetterie, actualités, gamification/pronostics, newsletter publique

### ❌ Ce qui reste = les fonctionnalités communauté

| # | Fonctionnalité | Service(s) touchés | Difficulté |
|---|---|---|---|
| A | Circuit validation comptes par documents (joueur, entraîneur, journaliste) | auth-service | ⭐⭐ |
| B | Espace entraîneur : convocations, envoi médias, programmation d'appels | sports-service (+ notification) | ⭐⭐ |
| C | Espace joueur : réception convocations/médias, réponses | sports-service | ⭐⭐ |
| D | Messagerie groupe joueurs type WhatsApp | nouveau module WebSocket (sports ou dédié) | ⭐⭐⭐ |
| E | Billets VIP PDF + QR code auto-réservés par joueur à chaque match à domicile | ticket-service + sports-service | ⭐⭐ |
| F | Accréditation journaliste PDF / refus motivé | content-service ou auth-service | ⭐⭐ |
| G | Appels vidéo/vocaux programmés | LiveKit/Daily cloud + frontend | ⭐⭐⭐⭐ |
| H | Espace Président : messages aux agents, reçus PDF (salaires/primes), réunions vidéo avec membres premium, joueurs et entraîneur | nouveau rôle + sports/payment/notification | ⭐⭐⭐ |

### Détail fonctionnalité H — Espace Président
Le Président a sa propre interface (nouveau rôle `PRESIDENT` à ajouter au `Role.java`). Il peut :
- **Écrire à ses agents** (staff administratif) — messagerie traçable pour la redevabilité : chaque message est horodaté et conservé en base
- **Envoyer aux joueurs des fichiers PDF de reçus de paiement** — salaires ou primes (générés côté backend depuis payment-service, ou uploadés) ; chaque joueur les retrouve dans son espace joueur, section « Documents financiers » (visible par lui seul)
- **Faire des appels vidéo** (via le même fournisseur que Phase 5) :
  - avec les **adhérents premium** (carte membre la plus chère) — individuellement OU en réunion de groupe, pour discuter des problèmes du club
  - avec les **joueurs** et l'**entraîneur**
- Réunion vidéo groupe = même mécanique qu'un appel entraîneur, avec plusieurs invités

---

## 2. Les phases de développement (dans l'ordre)

### Phase 0 — Socle rôles & statuts *(base de tout, à faire en premier)*
- [ ] Ajouter `ENTRAINEUR`, `JOURNALISTE` et `PRESIDENT` dans `auth-service/src/main/java/com/wydad/digital/auth/model/Role.java`
- [ ] Ajouter champ `statutCompte` : `EN_ATTENTE` / `VALIDE` / `REFUSE` (+ `motifRefus`)
- [ ] Ajouter niveau d'adhésion sur ADHERENT (`STANDARD` / `PREMIUM`) pour filtrer « carte la plus chère » (appels vidéo président/membres premium)
- [ ] Admin : écran « demandes de comptes » — liste des comptes EN_ATTENTE + documents joints, boutons Valider / Refuser (+ motif)
- [ ] Admin : création manuelle d'un compte joueur (nom, email, mot de passe fourni au joueur)

### Phase 1 — Upload des documents justificatifs
- [ ] Endpoint upload fichier (joueur, entraîneur, journaliste) à la création du compte → **Cloudinary** (voir §3)
- [ ] Stocker `publicId` + URL sécurisée Cloudinary côté backend (jamais l'URL brute en public)
- [ ] Journaliste : demande d'accréditation = formulaire + pièces jointes + statut EN_ATTENTE

### Phase 2 — Billets VIP joueurs (PDF + QR)
- [ ] À chaque création de match **à domicile** par l'admin (sports-service) → appel interne vers ticket-service
- [ ] Ticket-service génère automatiquement **4 billets VIP** par joueur actif de l'équipe, réservés à son compte
- [ ] Génération QR : bibliothèque **ZXing** (gratuite) ; PDF : **OpenPDF** ou **PDFBox**
- [ ] Espace joueur : liste de ses billets par match, téléchargement PDF (QR unique par billet, scannable à l'entrée)
- [ ] Idem pour l'accréditation journaliste validée → PDF d'accréditation généré pareillement

### Phase 3 — Convocations & médias tactiques
- [ ] Entraîneur : créer une convocation (match, date, heure, liste de joueurs cochés) → statut + notification in-app à chaque joueur
- [ ] Entraîneur : envoyer médias (vidéo Cloudinary, photo Cloudinary, PDF tactique, message) → à UN joueur ou à TOUTE l'équipe
- [ ] Joueur : boîte de réception dans son espace (convocations, médias, messages), possibilité de répondre (lu/non lu côté entraîneur)

### Phase 4 — Messagerie groupe joueurs (« WhatsApp »)
- [ ] WebSocket + STOMP natif Spring Boot (dépendance `spring-boot-starter-websocket`) exposé via api-gateway
- [ ] Groupes : « Équipe pro » (tous les joueurs + entraîneur) ; messages texte uniquement
- [ ] Frontend Angular : interface chat (liste messages temps réel, indicateur connexion)
- [ ] Historique persisté en base ; notifications in-app si hors ligne
- ⚠️ Sur la machine 1 Go RAM, limiter au texte (pas d'envoi de fichiers dans le chat — les médias passent par l'entraîneur, Phase 3)

### Phase 5 — Appels vidéo/vocaux programmés *(entraîneur + président)*
- [ ] Créer un compte gratuit **LiveKit Cloud** ou **Daily.co** (palier gratuit ~10 000 min/mois, largement suffisant en usage interne)
- [ ] Backend : endpoint qui vérifie le rôle/droit puis demande un **jeton d'accès** à l'API du fournisseur (quelques lignes)
- [ ] Entraîneur : programmer un appel (avec un joueur OU tous) → notification aux invités avec lien/date
- [ ] Président : réunion vidéo avec adhérents PREMIUM (individuelle ou groupe), avec joueurs, avec l'entraîneur
- [ ] Frontend : page « rejoindre l'appel » avec SDK web du fournisseur (caméra + micro du navigateur, rien à installer)
- ⚠️ Le serveur média tourne chez le fournisseur → aucune charge sur le serveur

### Phase 5 bis — Espace Président
- [ ] Rôle `PRESIDENT` + interface dédiée (même socle que les autres espaces)
- [ ] Messagerie vers les agents (staff) — horodatée et persistée pour la redevabilité
- [ ] Envoi de reçus PDF de paiement (salaires/primes) aux joueurs — génération PDF côté backend (OpenPDF), stockage Cloudinary en folder privé, visible par chaque joueur dans SON espace uniquement
- [ ] Lancement de réunions vidéo (réutilise l'infrastructure Phase 5)

### Phase 6 — Tests (ISTQB + automatisation)
Qualité structurée selon le syllabus **ISTQB Foundation Level** — niveaux de test appliqués au projet :

| Niveau ISTQB | Quoi | Outils (gratuits) |
|---|---|---|
| Test unitaire | Chaque service : logique métier (validation comptes, calcul billets VIP, génération QR/PDF) | JUnit 5 + Mockito (déjà dans Spring Boot) |
| Test d'intégration | Repositories, appels inter-services (sports→ticket, auth→Cloudinary) | Spring Boot Test + Testcontainers PostgreSQL/Redis |
| Test système / E2E | Parcours complets : inscription→validation admin→convocation→billet ; messagerie temps réel | Cypress ou Playwright sur le frontend Angular |
| Test d'acceptation | Validation des critères d'acceptation de chaque phase avec les scénarios Gherkin | Cucumber (optionnel) |

Automatisation :
- [ ] JaCoCo pour la couverture de code (objectif ≥ 70 % sur la logique métier critique)
- [ ] Tests lancés à chaque build Maven (`mvn verify`) — intégrés au pipeline CI (GitHub Actions gratuit sur repo public)
- [ ] Scénarios de non-régression automatisés pour les parcours critiques avant chaque déploiement
- [ ] Cas particuliers ISTQB à couvrir explicitement : refus de compte avec motif, billet VIP dupliqué/scanné deux fois, accès d'un joueur STANDARD aux appels (doit être refusé), upload fichier trop volumineux, message vide en chat

### Phase 7 — Finitions avant mise en production
- [ ] Tests bout-en-bout de chaque parcours (création compte → validation → usage)
- [ ] Relecture sécurité : qui peut appeler quoi (vérifier les guards JWT par rôle sur CHAQUE nouvel endpoint)
- [ ] Nettoyage des données de test / seed

---

## 3. Cloudinary (vidéos & photos) — plan gratuit

**Offerte gratuitement** : 25 Go de stockage + 25 Go de bande passante/mois — très largement suffisant pour des vidéos tactiques internes.

### Ce que ça remplace
Plus besoin de volume Docker pour les médias : les vidéos/photos/PDF des entraîneurs ET les pièces justificatives des comptes partent tous sur Cloudinary.

### Intégration côté backend (un seul endroit)
- [ ] Créer un compte gratuit sur cloudinary.com → récupérer `cloud_name`, `api_key`, `api_secret`
- [ ] Ajouter la dépendance `cloudinary-http5` (SDK Java officiel, gratuit) dans les services concernés (auth pour les justificatifs, sports pour les médias)
- [ ] Un utilitaire commun d'upload : le frontend envoie le fichier au backend → le backend signe et pousse vers Cloudinary → ne stocke que `publicId` + URL en base
- [ ] Config dans `.env` / docker-compose :
```yaml
environment:
  - CLOUDINARY_CLOUD_NAME=${CLOUDINARY_CLOUD_NAME}
  - CLOUDINARY_API_KEY=${CLOUDINARY_API_KEY}
  - CLOUDINARY_API_SECRET=${CLOUDINARY_API_SECRET}
```

### Points de vigilance
- Limiter la taille des uploads côté backend (ex. 100 Mo/vidéo, 5 Mo/document PDF)
- Utiliser des **URLs signées** ou des folders privés pour les justificatifs (documents sensibles !) — seuls l'admin et le propriétaire peuvent y accéder
- Les vidéos tactiques peuvent être publiques-restreintes (URL non devinable)

---

## 4. Déploiement complet (frontend + backend + tout) — GRATUIT

### 4.0 🏆 Fournisseurs d'hébergement gratuits — comparatif

| Fournisseur | Offre gratuite | Verdict pour ce projet |
|---|---|---|
| **Oracle Cloud Free Tier** ⭐ | ARM Ampere A1 : jusqu'à **4 OCPU + 24 Go RAM** + 200 Go stockage bloc, à vie (Always Free) | **LE choix n°1** — le seul gratuit capable de faire tourner les 9 services Java confortablement. Docker s'y installe comme sur n'importe quel Ubuntu |
| AWS Free Tier | EC2 t.micro (1 Go RAM) 12 mois seulement | ❌ Insuffisant en RAM et expiré au bout d'un an |
| Google Cloud | e2-micro (1 Go RAM, 30 Mo disque éphémère) « always free » | ❌ Trop juste pour 9 services Java |
| Azure | B1s (1 Go RAM) 12 mois | ❌ Idem AWS |
| Fly.io / Railway / Render | Petites instances gratuites avec sommeil auto | ❌ Sommeil automatique = site qui s'endort ; RAM limitée ; politiques changeantes |

### Recommandation : Oracle Cloud Always Free (ARM)
- [ ] Créer un compte Oracle Cloud (carte bancaire demandée pour vérification, **sans débit** sur le tier gratuit)
- [ ] Créer une instance **VM.Standard.A1.Flex** avec **4 OCPU / 24 Go RAM**, image Ubuntu 22.04+ — choisir une région/disponibilité où la capacité A1 est disponible (c'est le principal irritant : parfois « out of capacity », insister ou changer d'availability domain)
- [ ] Ouvrir les ports 80/443 dans la Security List du VCN (en plus du firewall Ubuntu `ufw`)
- [ ] Avec 24 Go de RAM, **plus besoin de brider la JVM** — chaque service peut tourner normalement, pgAdmin inclus si tu veux
- ⚠️ Bonnes pratiques : télécharger la clé SSH privée à la création (non récupérable ensuite), activer les backups du boot volume, ne pas dépasser les quotas Always Free pour éviter toute facturation

> Même si l'app est « grande », cette architecture microservices tient très bien sur 4 OCPU/24 Go : Postgres ~500 Mo, Redis ~50 Mo, chaque service Spring Boot 300–500 Mo → total ≈ 5-6 Go, soit un quart de la RAM offerte.

### 4.1 Alternative si Oracle indisponible
VPS 4 Go payant (~5–7 €/mois, Hetzner CX22 / OVH) — même procédure que ci-dessous, rien ne change hormis le fournisseur.

⚠️ **Important ARM** : les images Docker des services doivent être construites pour `linux/arm64`. Le plus simple : builder directement sur l'instance Oracle (`docker compose build` sur le serveur — les Dockerfiles multi-étapes Maven le supportent nativement). Sinon, `docker buildx --platform linux/arm64` en local puis push sur Docker Hub (gratuit).

Sur 24 Go de RAM, inutile de brider : ne pas mettre de `mem_limit`, laisser la JVM par défaut. (Si un jour tu repasses sur une petite machine : `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=50.0` + `mem_limit: 400m` par service.)

### 4.2 Pré-requis serveur
- [ ] Une instance Oracle Cloud Ubuntu 22.04+ (voir §4.0) — connexion SSH avec ta clé privée
- [ ] Installer Docker + Docker Compose sur l'instance :
```bash
sudo apt update && sudo apt install -y docker.io docker-compose-v2
sudo usermod -aG docker $USER   # puis se déconnecter/reconnecter
```
- [ ] Un nom de domaine (ex. `wydad-fan.ma`) — gratuit possible via un domaine `eu.org`/`duckdns.org`, sinon ~1–2 €/an (.ma payant chez un registrar). Enregistrement DNS A → IP publique de l'instance (sous-domaine `www` aussi)
- [ ] Cloner le repo sur le serveur : `git clone <repo> && cd wydad-digital-parent`

### 4.2 bis 💻 Développer SANS faire chauffer son PC (VS Code Remote SSH)

Une fois l'instance Oracle créée, elle devient aussi **l'environnement de développement** : le serveur compile, teste et exécute ; le PC ne fait qu'éditer du texte.

**Setup (une seule fois)** :
1. Dans VS Code : installer l'extension **Remote - SSH** (gratuite)
2. `F1` → « Remote-SSH: Connect to Host » → `ubuntu@<IP_PUBLIQUE>` (mot de passe : la clé privée configurée dans `~/.ssh/config`)
3. Ouvrir le dossier `/home/ubuntu/wydad-digital-parent` → on édite les fichiers du serveur comme s'ils étaient locaux
4. Le terminal intégré de VS Code est un terminal du serveur → tous les builds/tests y tournent

**Boucle de travail quotidienne** :
```bash
# dans le terminal VS Code (= terminal du serveur)
docker compose up -d --build     # recompile uniquement ce qui a changé (cache Docker)
docker compose logs -f api-gateway
```
→ Tester dans le navigateur via `http://<IP>` (ou le domaine une fois Caddy en place).

**Avantages** : zéro chauffe du PC, tests sur l'architecture cible exacte (ARM), pas de synchronisation git nécessaire pendant le dev (le repo EST sur le serveur). Penser quand même à pousser sur GitHub régulièrement pour la sauvegarde.

**Sécurité des clés — RÈGLE ABSOLUE** : la clé privée SSH reste sur le PC, ne JAMAIS la partager (chat, email, repo). Le `.env` des secrets vit uniquement sur le serveur, hors git.


Créer `.env` sur le serveur :
```env
POSTGRES_USER=wydad
POSTGRES_PASSWORD=<mot-de-passe-fort>
JWT_SECRET=<chaîne-aléatoire-longue>
ADMIN_SEED_PASSWORD=<mot-de-passe-admin-fort>
INTERNAL_SECRET=<secret-inter-services>
GATEWAY_HOST_PORT=8080
FRONTEND_HOST_PORT=4200        # sera derrière le reverse proxy HTTPS
CORS_ALLOWED_ORIGINS=https://wydad-fan.ma,https://www.wydad-fan.ma
# Cloudinary
CLOUDINARY_CLOUD_NAME=...
CLOUDINARY_API_KEY=...
CLOUDINARY_API_SECRET=...
# Vidéo (Phase 5)
LIVEKIT_URL=... / DAILY_API_KEY=...
```
Générer les secrets : `openssl rand -base64 48`

⚠️ Dans le compose de prod : **supprimer `pgadmin`** (outil de dev) et **ne pas exposer Postgres/Redis sur les ports hôte** (actuellement 5433/6379 sont exposés → à retirer en prod).

### 4.4 HTTPS obligatoire (reverse proxy)
Un seul point d'entrée public : le reverse proxy termine le TLS et route `/` → frontend, `/api` → gateway.

Option simple et gratuite : **Caddy** (certificats automatiques Let's Encrypt) :
```yaml
# à ajouter au docker-compose.yml prod
caddy:
  image: caddy:2-alpine
  ports: ["80:80", "443:443"]
  volumes:
    - ./Caddyfile:/etc/caddy/Caddyfile
    - caddy_data:/data
```
`Caddyfile` :
```
wydad-fan.ma, www.wydad-fan.ma {
  handle /api/* {
    reverse_proxy api-gateway:8080
  }
  handle {
    reverse_proxy frontend:80
  }
}
```
Puis passer `FRONTEND_HOST_PORT`/`GATEWAY_HOST_PORT` en interne seulement (retirer les `ports:` hôte de gateway et frontend, garder `expose:`).

Alternative : Nginx + certbot (certificats à renouveler) si tu préfères.

### 4.5 Build & lancement
```bash
docker compose -f docker-compose.yml up -d --build
docker compose ps                 # vérifier que tout est healthy
docker compose logs -f api-gateway
```
Ordre géré automatiquement par les `depends_on: condition: service_healthy` déjà en place (postgres → services → gateway → frontend).

### 4.6 Après le premier démarrage — checklist
- [ ] Se connecter en admin seed, **changer immédiatement le mot de passe**
- [ ] Tester : inscription → login → achat billet → consultation actualités (parcours publics déjà prêts)
- [ ] Vérifier CORS : le site en HTTPS doit appeler l'API sans erreur console
- [ ] Sauvegardes : cron quotidien `docker exec wydad-postgres pg_dumpall > backup.sql` + copie hors serveur (au moins hebdomadaire)
- [ ] Monitoring minimal : `docker compose ps` en cron + alerte email si un conteneur n'est pas healthy (script bash simple suffit)
- [ ] `docker compose pull && docker compose up -d --build` pour les mises à jour futures

---

## 5. Planning suggéré global

| Étape | Contenu | Effort estimé |
|---|---|---|
| Semaine 1–2 | Phase 0 + 1 (rôles dont PRESIDENT, statuts, upload Cloudinary, écran admin validation) | socle critique |
| Semaine 3 | Phase 2 (billets VIP PDF+QR + accréditation presse) | mécanique une fois ZXing/PDF en place |
| Semaine 4 | Phase 3 (convocations, médias, réponses joueurs) | moyen |
| Semaine 5 | Phase 4 (messagerie WebSocket) + Phase 5 bis (espace Président) | le plus technique backend |
| Semaine 6 | Phase 5 (appels vidéo LiveKit/Daily) | simple côté code, tests nécessaires |
| Semaine 7 | Phase 6 : tests ISTQB/automatisés, couverture JaCoCo, E2E Playwright | qualité continue |
| Semaine 8 | Phase 7 + déploiement (§4) : Oracle Cloud ARM, domaine, HTTPS Caddy, sauvegardes | administratif + config |

> Les semaines sont indicatives — l'ordre importe plus que les durées. Ne pas attaquer les phases 4/5 avant que la phase 0 soit solide : tout dépend du circuit de validation. Les tests (Phase 6) ne sont pas une option ISTQB oblige : ils accompagnent chaque phase idéalement, pas seulement en fin de projet.
