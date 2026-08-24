# 🗺️ Roadmap restante + Déploiement — Wydad Digital

> Document généré le 2026-08-24, **révisé en fin de journée** après l'audit complet, la vague 0 sécurité, la migration design clair, les Phases 0/1/1bis et le déploiement Azure effectifs. Il fait le point sur **ce qui reste à développer** et **l'hébergement**.
>
> Contraintes : solutions **gratuites**, déploiement **Docker-only** (pas de Kubernetes), qualité selon **ISTQB** avec tests automatisés.

---

## 1. Où on en est — ÉTAT RÉEL au 2026-08-24 soir

### ✅ Terminé et déployé sur Azure (158.158.74.169)

| Domaine | Contenu |
|---|---|
| **Architecture** | 9 microservices Spring Boot + gateway + frontend Angular 19 (nginx), PostgreSQL ×9 bases, Redis, Docker Compose |
| **Sécurité (Vague 0)** | S1–S7 corrigées : login/refresh vérifient `isActive`+`VALIDE`, refresh token typ vérifié, membershipLevel forcé ROUGE côté serveur, sessions révocables, @PreAuthorize partout, reset password OTP complet |
| **Rôles & statuts (Phase 0)** | `ENTRAINEUR`, `JOURNALISTE`, `PRESIDENT` + `StatutCompte` EN_ATTENTE/VALIDE/REFUSE avec motif ; circuit admin : écran « Demandes de comptes » (valider/refuser motivé) ; création manuelle de joueurs par l'admin |
| **Cloudinary KYC (Phase 1)** | Upload réel multipart → Cloudinary type `authenticated`, folder privé `kyc-documents/<email>`, seuls publicId+secureUrl en DB, mode dégradé sans clés ; consultation admin via URL signée 1 h (bouton 📄 Justificatif sur « Demandes de comptes ») — validé E2E en prod |
| **Design clair blanc/rouge** | Socle tokens paper-*/ink-* + layout public + accueil migrés ; ~18 pages encore sombres (voir §2 Vague 1) |
| **Tests** | Backend : 196 tests verts (auth 34, content 56, sports, shop, ticket…). Frontend : **25 specs Jasmine/Karma opérationnelles** (infra créée ce jour) — services toast/confirm/auth/api |
| **Déploiement** | VM Azure Standard_B2s_v2 Spain Central (2 vCPU/8 Go), boucle git pull→mvn→compose au fil de l'eau, journal dans DEPLOIEMENT-AZURE.md |

### ❌ Ce qui reste = fonctionnalités communauté

| # | Fonctionnalité | Service(s) touchés | Difficulté | Statut |
|---|---|---|---|---|
| A | Circuit validation comptes par documents | auth-service | ⭐⭐ | ✅ **FAIT** (Phase 0) |
| B | Espace entraîneur : convocations, médias, appels programmés | sports-service (+ notification) | ⭐⭐ | Partiel : convocations staff→joueur existent déjà (audit) ; reste médias Cloudinary + programmation d'appels |
| C | Espace joueur : boîte de réception, réponses | sports-service | ⭐⭐ | Partiel : messagerie HTTP polling existe ; reste UI unifiée convocations+médias |
| D | Messagerie groupe joueurs type WhatsApp | WebSocket (sports ou dédié) | ⭐⭐⭐ | À faire (texte uniquement, RAM 8 Go OK) |
| E | Billets VIP PDF + QR auto-réservés par joueur à domicile | ticket-service + sports-service | ⭐⭐ | À faire |
| F | Accréditation journaliste PDF / refus motivé | content ou auth-service | ⭐⭐ | À faire |
| G | Appels vidéo/vocaux programmés | LiveKit/Daily + frontend | ⭐⭐⭐⭐ | À faire (dernier, optionnel) |
| H | Espace Président (messages agents, reçus PDF salaires/primes, réunions vidéo premium) | nouveau rôle + sports/payment/notification | ⭐⭐⭐ | Rôle PRESIDENT créé (Phase 0), reste interface + reçus PDF + réunions |

### Détail fonctionnalité H — Espace Président
Le Président a sa propre interface (rôle `PRESIDENT` existe déjà depuis la Phase 0). Il peut :
- **Écrire à ses agents** (staff administratif) — messagerie horodatée et persistée pour la redevabilité
- **Envoyer aux joueurs des reçus PDF de paiement** (salaires/primes) — génération OpenPDF côté backend, stockage Cloudinary folder privé, visible par chaque joueur seul
- **Faire des appels vidéo** avec les **adhérents premium**, les **joueurs** et l'**entraîneur** (individuel ou groupe)

---

## 2. Les phases de développement (dans l'ordre)

### Phase 1 ter — Finaliser la validation des comptes *(petit lot avant les grosses phases)*
- [ ] Admin : afficher statut KYC (`verified`) et justificatif consultable directement dans la liste des demandes (le bouton 📄 existe ; ajouter le statut vérifié/nom du document)
- [ ] Journaliste : demande d'accréditation = formulaire dédié + pièces jointes → statut EN_ATTENTE (Phase F)
- [ ] Notification in-app à l'utilisateur quand son compte est validé/refusé (notification-service)
- [ ] Surface d'erreur Cloudinary explicite côté utilisateur (« service d'upload momentanément indisponible »)

### Phase 2 — Billets VIP joueurs (PDF + QR)
- [ ] À chaque création de match **à domicile** (sports/content-service) → appel interne vers ticket-service
- [ ] Ticket-service génère automatiquement **4 billets VIP** par joueur actif, réservés à son compte
- [ ] QR : bibliothèque **ZXing** (gratuite) ; PDF : **OpenPDF** ou **PDFBox**
- [ ] Espace joueur : liste de ses billets par match, téléchargement PDF (QR unique scannable)
- [ ] Idem accréditation journaliste validée → PDF généré pareillement
- Cas ISTQB explicites : billet dupliqué / scanné deux fois, joueur sans billet, match extérieur (aucun billet)

### Phase 3 — Convocations & médias tactiques
- [ ] Entraîneur : créer une convocation (match, date, heure, liste cochable) → notification in-app à chaque joueur
- [ ] Entraîneur : envoyer médias (vidéo/photo Cloudinary, PDF tactique, message) → UN joueur ou TOUTE l'équipe (réutiliser CloudinaryService comme modèle auth-service)
- [ ] Joueur : boîte de réception unifiée (convocations, médias, messages) + réponses (lu/non lu côté entraîneur)

### Phase 4 — Messagerie groupe joueurs (« WhatsApp »)
- [ ] WebSocket + STOMP natif Spring Boot exposé via api-gateway
- [ ] Groupe « Équipe pro » (joueurs + entraîneur), texte uniquement
- [ ] Frontend Angular : chat temps réel + indicateur connexion
- [ ] Historique persisté ; notifications si hors ligne
- ⚠️ Texte seulement (médias = Phase 3 entraîneur)

### Phase 5 — Appels vidéo/vocaux programmés *(entraîneur + président)*
- [ ] Compte gratuit **LiveKit Cloud** ou **Daily.co** (~10 000 min/mois gratuits)
- [ ] Backend : endpoint jeton d'accès avec contrôle rôle/droit (quelques lignes)
- [ ] Entraîneur : programmer un appel (un joueur OU tous) → notification lien/date
- [ ] Président : réunions avec adhérents PREMIUM, joueurs, entraîneur
- [ ] Frontend : page « rejoindre l'appel » avec SDK web du fournisseur
- ⚠️ Serveur média chez le fournisseur → aucune charge sur la VM

### Phase 5 bis — Espace Président
- [ ] Interface dédiée rôle PRESIDENT (socle existant)
- [ ] Messagerie vers les agents — horodatée, persistée
- [ ] Reçus PDF salaires/primes (OpenPDF + Cloudinary folder privé, visible par le joueur seul)
- [ ] Lancement de réunions vidéo (réutilise Phase 5)

### Phase 6 — Tests (ISTQB + automatisation) — *en cours, voir §6*
- [x] Infra tests frontend créée (Karma/Jasmine, 25 specs vertes)
- [ ] Étendre specs Angular aux composants critiques (login, register, profil KYC, admin-demandes)
- [ ] JaCoCo couverture ≥ 70 % logique métier critique
- [ ] Testcontainers PostgreSQL/Redis pour les tests d'intégration (déclarés jamais utilisés — audit)
- [ ] Scénarios ISTQB restants : billet VIP dupliqué/scanné 2×, accès STANDARD aux appels refusé, upload trop volumineux, message vide chat, refus compte avec motif
- [ ] E2E navigateur (Playwright) sur parcours critiques : inscription→validation admin→convocation→billet

### Phase 7 — Finitions avant mise en production
- [ ] Tests bout-en-bout de chaque parcours (création compte → validation → usage)
- [ ] Relecture sécurité : guards JWT par rôle sur CHAQUE nouvel endpoint
- [ ] Nettoyage données de test / seed
- [ ] Changer le mot de passe admin seed (`admin@wac.ma`) — toujours pas fait ⚠️

---

## 3. Cloudinary — fait & à venir

**Compte créé et intégré** : cloud `dudsw3ect`, upload `authenticated`, folder privé `kyc-documents/`. 25 Go stockage + 25 Go bande passante/mois gratuits.

### Ce qui est branché
- auth-service : justificatifs KYC (Phase 1) — upload multipart, URL signées admin 1 h
- docker-compose + .env serveur : variables CLOUDINARY_* en place

### À brancher (réutiliser le modèle CloudinaryService d'auth-service)
- [ ] sports-service : médias tactiques entraîneur (Phase 3) — vidéos/photos publiques-restreintes (URL non devinable), PDF tactique
- [ ] auth/sports : reçus financiers président (Phase 5 bis) — folder privé séparé `president-receipts/`
- [ ] content-service : images des articles d'actualités (optionnel, remplace les placeholders)

---

## 4. Déploiement — état & suite

### 4.1 Situation actuelle : Azure (déployé, en ligne)
- VM **Standard_B2s_v2** (2 vCPU/8 Go) — Spain Central, IP publique **158.158.74.169**
- Frontend http://158.158.74.169:4200 · API gateway :8080 (interne, CORS configuré)
- Boucle dev→deploy au fil de l'eau : `git push` → ssh `git pull && mvn package && docker compose up -d --build <services>`
- Coût ~38–40 $/mois → crédit étudiant 100 $ ≈ 2,5 mois 24/7, **4–6 mois avec deallocate discipliné**
- Journal complet : `DEPLOIEMENT-AZURE.md`

### 4.2 Migration Oracle Cloud Always Free (recommandée à terme)
Oracle A1 Flex : **4 OCPU / 24 Go RAM à vie gratuite** — le seul hébergeur gratuit confortable pour 9 services Java. Azure est parfait pour la période projet/démo ; Oracle pour la pérennité.

- [ ] Créer le compte Oracle (carte requise pour vérification, sans débit)
- [ ] Instance VM.Standard.A1.Flex 4 OCPU/24 Go, Ubuntu 22.04+, région avec capacité A1 disponible (principal irritant : « out of capacity », insister/changer d'AD)
- [ ] Ports 80/443 Security List VCN + ufw ; SSH par clé
- [ ] Build ARM64 : builder directement sur l'instance (`docker compose build` natif arm64) — les Dockerfiles actuels copient des jars précompilés, donc `mvn package` sur la VM puis compose build, même boucle qu'Azure
- [ ] Transférer le `.env` (recréer les secrets, ne PAS copier-coller les anciens hors serveur) et migrer les données Postgres (`pg_dumpall` Azure → restore Oracle)
- [ ] DNS bascule quand tout est validé

⚠️ ARM note : les images construites sur place tournent nativement arm64 — rien de spécial si on build sur l'instance.

### 4.3 HTTPS obligatoire avant mise en production publique
Un seul point d'entrée : reverse proxy TLS. Option simple gratuite : **Caddy** (certificats auto Let's Encrypt) :
```yaml
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
  handle /api/* { reverse_proxy api-gateway:8080 }
  handle { reverse_proxy frontend:80 }
}
```
Puis passer frontend/gateway en `expose:` interne seulement. Nécessite un domaine (eu.org/duckdns gratuit possible, ~1–2 €/an pour un .ma).

### 4.4 Checklist prod (avant ouverture publique)
- [ ] Changer le mot de passe admin seed ⚠️ (toujours en attente)
- [ ] Supprimer pgAdmin du compose de prod ; retirer ports hôte Postgres/Redis (5433/6379 exposés)
- [ ] HTTPS (§4.3) + CORS_ALLOWED_ORIGINS mis à jour vers le domaine final
- [ ] Sauvegardes cron quotidien `pg_dumpall` + copie hors serveur hebdomadaire
- [ ] Monitoring minimal : cron `docker compose ps` + alerte email conteneur unhealthy
- [ ] Régénérer la clé API Cloudinary (le secret est passé par le chat — à remplacer en vrai prod)

---

## 5. Planning suggéré global

| Étape | Contenu | Effort estimé |
|---|---|---|
| Semaine 1 | Phase 1 ter (polissage validation) + Vague 1 design (18 pages sombres) | petit + moyen |
| Semaine 2 | Phase 2 (billets VIP PDF+QR + accréditation presse) | mécanique une fois ZXing/PDF en place |
| Semaine 3 | Phase 3 (médias Cloudinary entraîneur, boîte joueur) | moyen |
| Semaine 4 | Phase 4 (messagerie WebSocket) + Phase 5 bis (espace Président hors vidéo) | technique backend |
| Semaine 5 | Phase 5 (appels vidéo LiveKit/Daily) + Phase 6 (tests E2E, JaCoCo, Playwright) | simple code / qualité continue |
| Semaine 6 | Phase 7 + checklist prod (HTTPS, domaine, sauvegardes) ; décision migration Oracle | config + administratif |

> Les semaines sont indicatives — l'ordre importe plus que les durées. Les tests accompagnent chaque phase (ISTQB oblige), ils ne sont pas une phase isolée.

---

## 6. Annexe — état détaillé des tests (au 2026-08-24 soir)

| Suite | État | Notes ISTQB |
|---|---|---|
| auth-service | 34/34 ✅ | Circuit validation, OTP, JWT, sécurité vague 0 (unit + intégration MockMvc) |
| content-service | 56 ✅ | Sécurité endpoints (Sponsor/Trophy/Stadium/Legend/Joueur/Reclamation/SocialLinks), FileTypeValidator |
| sports-service | 9 classes ✅ | Ownership joueur, polls, player space, messaging, medical, academy docs |
| shop/ticket/payment/gamification/notification/gateway | ✅ | Sécurité + concurrency wallet + exceptions handlers |
| Frontend Angular | 25/25 ✅ | Karma/Jasmine — services toast/confirm/auth/api ; partitions JWT, frontières exp, contrat HTTP |
| Manquant | — | Testcontainers (déclarés jamais utilisés), JaCoCo, E2E navigateur, composants Angular |
