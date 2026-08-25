# 🏆 Wydad Digital — Rapport détaillé du projet

> Rapport de synthèse au **25/08/2026** : fonctionnalités réalisées, architecture, tests et campagne de re-test complète en production.
> Environnement de prod : VM Azure **Standard_B2s_v2** (2 vCPU / 8 Go) Spain Central — `158.158.74.169`, tout en Docker Compose (pas de Kubernetes).

---

## 1. Vision du projet

Plateforme digitale officielle du Wydad AC couvrant l'ensemble de son écosystème :
**supporters, adhérents, joueurs, staff technique, presse et direction**.
Architecture microservices Java 21 / Spring Boot 3.3 + frontend Angular 19 standalone,
déployée sur une seule VM Docker, avec CI/CD GitHub Actions et sauvegardes quotidiennes.

Principes directeurs :
- **Un service = une responsabilité** (thématique stricte)
- **Sécurité par défaut** : secrets hors git, validation serveur systématique, endpoints internes injoignables depuis l'extérieur
- **Solutions gratuites** : LiveKit self-hosted pour la vidéo, Cloudinary pour les médias, pas de partie live payante
- **Chaque fonctionnalité prouvée par un test automatisé ET un E2E en prod**

---

## 2. Architecture livrée

### 2.1 Les 11 microservices (+ gateway + frontend)

| Service | Port | Base | Responsabilité |
|---|---:|---|---|
| api-gateway | 8080 | — | Porte d'entrée unique : routage, validation JWT globale, blocage routes internes, dérogations publiques explicites |
| auth-service | 8081 | auth_db | Comptes, JWT + refresh rotatif, OTP, rôles, KYC Cloudinary, circuit de validation des comptes |
| content-service | 8082 | content_db | Actualités & pages publiques du club |
| payment-service | 8083 | payment_db | Paiements simulés, cycle d'état, référencé par shop/ticket |
| shop-service | 8084 | shop_db | Boutique officielle (produits, variantes, panier, commandes) |
| ticket-service | 8085 | ticket_db | Billets VIP + QR (quota 4/adhérent), scan sécurisé |
| notification-service | 8086 | notification_db | Notifications in-app centralisées + newsletter publique |
| sports-service | 8087 | sports_db | Domaine sportif : effectif, convocations, séances, stats, médias, médical, academy, appels LiveKit programmés, API roster interne |
| gamification-service | 8088 | wydad_gamification | Points/badges fans avec anti-triche |
| election-service | 8089 | elections_db | Gouvernance : élections du président + sondages |
| communication-service | 8090 | communication_db | Messagerie privée, annonces staff, chat de groupe temps réel (WebSocket STOMP) |

**Frontend** : nginx Angular 19 sur le port 4200 — site public + espaces connectés
(adhérent, joueur, entraîneur/staff, journaliste, président, admin).
**PostgreSQL 16** unique multi-bases + Redis.

### 2.2 Découplage inter-services
- API roster interne `/api/sports/internal/roster/**` protégée `X-Internal-Secret` (comparaison SHA-256 à temps constant)
- Notifications best-effort via `/api/notification/internal/send`
- Endpoints `/internal/**` **bloqués côté gateway** (routes dédiées en tête de chaîne)
- Headers d'identité `X-User-*` posés uniquement par la gateway après validation JWT (sanitisation systématique des headers client)

---

## 3. Fonctionnalités réalisées (par domaine)

### 3.1 Comptes & sécurité (auth-service)
- Inscription/connexion JWT avec **refresh token rotatif**, refus de login si compte inactif
- Circuit complet de validation des comptes **par documents** (KYC Cloudinary) : EN_ATTENTE → VALIDE/REFUSE avec motif
- Rôles fins : ADHERENT, JOUEUR, PARENT, ENTRAINEUR, JOURNALISTE, PRESIDENT, ADMIN
- Reset mot de passe protégé par OTP ; member-card et attestation réservées au titulaire (JWT)
- Notifications internes protégées par secret partagé à temps constant

### 3.2 Contenu public (content-service)
- Actualités du club avec cycle brouillon → publié, permissions éditoriales
- Pages publiques consultables sans compte (lecture GET ouverte côté gateway, écriture toujours authentifiée)

### 3.3 Boutique & billetterie (shop + payment + ticket)
- Catalogue boutique public, panier, commandes avec gestion de stock et conflits optimistes (409)
- Paiements simulés avec machine à états
- **Billets VIP : quota strict de 4/adhérent imposé serveur**, QR signés, **scan sécurisé** (anti-rejeu, anti-falsification — TicketScanSecurityTest)
- Événements billetterie publics, achat authentifié

### 3.4 Espace sportif (sports-service)
- Effectif joueurs/staff avec fiches complètes (physique, stats matchs, notes médicales)
- **Convocations** : création STAFF/ADMIN only, notification IN_APP automatique à chaque joueur du groupe visé
- Planning des séances d'entraînement filtrable (sport/catégorie)
- Médias tactiques (photo/vidéo/PDF) sur Cloudinary avec limites d'upload
- Dossier médical tenu par le staff ; espace joueur « my-space » strictement personnel (ownership vérifié, 403 pour les autres rôles y compris admin)
- Academy (documents parents/enfants)
- **Appels vidéo/vocaux programmés LiveKit** (Phase 5) : programmer → agenda → jeton LiveKit généré → annulation ; intégration front `join()` avec adaptiveStream

### 3.5 Communication (communication-service)
- **Messagerie privée joueur↔staff par catégorie** (WhatsApp-like) : inbox, conversation 1-à-1, contrôle que chaque correspondant appartient bien au même cadre sportif
- **Annonces staff**
- **Chat de groupe temps réel WebSocket STOMP** : adhésion déduite du roster (impossible de s'inviter dans un groupe), JWT validé à la frame CONNECT, limites de taille, supervision admin sans fiche roster, historique persistant

### 3.6 Gouvernance du club (election-service)
- **Élections du président (B.8)** : création par l'admin, candidats (nom/photo/présentation), vote à un tour anti-double-vote (409), clôture + publication, **résultats publics sans connexion** avec % par candidat
- Sondages actifs en lecture publique, vote authentifié
- Migrés depuis sports-service lors de l'audit thématique (« un service = un thème »)

### 3.7 Engagement des fans (gamification-service)
- Attribution de points/badges avec règles anti-triche testées

### 3.8 Notifications (notification-service)
- Canal interne centralisé utilisé par convocations, messagerie, etc.
- **Newsletter publique** : inscription anonyme depuis le footer (validation format + unicité email serveur)

### 3.9 Frontend Angular 19 (standalone)
- Site public : accueil, actualités, boutique, billetterie, résultats d'élections, sondages, newsletter footer
- Espaces : adhérent (billets VIP+QR, mes élections, messagerie), joueur (my-space, agenda d'appels, chat), staff (convocations, médias, annonces, programmation d'appels, formulaire), admin (validation comptes/KYC, gestion élections+sondages, supervision chat)
- Guards de rôles, intercepteur JWT, toasts/confirms, thème clair/sombre en cours d'unification (~18 pages sombres restantes identifiées à l'audit)

---

## 4. Tests — état chiffré

### 4.1 Backend : 285 tests JUnit 5 (techniques ISTQB)

| Service | Tests | Focus |
|---|---:|---|
| auth | 45 | login isActive, refresh rotatif, KYC, refus avec motif |
| content | 56 | CRUD actualités, permissions publication |
| payment | 6 | transitions d'état |
| shop | 15 | stock, décisions achat, concurrence 409 |
| ticket | 28 | quota 4 VIP, QR, scan sécurisé |
| notification | 25 | envoi interne protégé, newsletter |
| sports | **55** | convocations, séances, stats, médias, médical, ownership, appels (14), **roster interne (4)** |
| gamification | 15 | points, anti-triche |
| election | 24 | cycle complet, accès public vs vote authentifié |
| communication | **16** | messagerie privée (5), chat REST (6), canal STOMP (4), **handshake WS (1)** |

Les deux chiffres en gras incluent les tests de non-régression ajoutés par la campagne de re-test du 25/08 (§6).

### 4.2 Frontend
- Build prod vert en CI ; 38 specs passantes (api/auth/confirm/toast services, my-calls, team-chat, vip-tickets)

### 4.3 Gateway : 6 tests unitaires
- Lecture publique jamais 401 sans token (8 chemins publics), POST catalogue toujours 401, routes internes bloquées même avec JWT valide, newsletter anonyme passe mais broadcast reste 401

### 4.4 E2E prod prouvés (scripts réexécutables dans `scripts/`)
| Parcours | Preuve |
|---|---|
| Phase 4 — chat WS | 2 sessions SockJS, frame MESSAGE reçue temps réel + persistance REST (**réprouvé le 25/08 après correctifs**) |
| Phase 5 — appels LiveKit | programmer → agenda → jeton 3 parties → annulation 400 |
| B.8 — élections | register → vote → revote 409 → clôture → résultats publics sans token |
| Routage gateway | matrice publics/internes validée sur VM |
| Sauvegardes | pg_dump gzip 1,3 Mo, cron quotidien 04h17 |

---

## 5. DevOps / CI-CD

- **GitHub Actions** : pour chacun des 11 services — build+tests, upload JAR, build/push image Docker Hub (`:latest` + `:sha`) avec cache GHA ; plus jobs frontend
- **Déploiement** : boucle `git pull && mvn package && docker compose up -d --build` sur la VM (journalisée dans DEPLOIEMENT-AZURE.md)
- **Sauvegardes** : cron quotidien 04h17 (`backup-db.sh`), rétention 7 jours, pools Hikari calibrés (×5) pour ne pas saturer PostgreSQL
- **Secrets** : `.env` serveur chmod 600 + gestionnaire de mots de passe uniquement — jamais dans git ni le chat

---

## 6. Campagne de re-test complète (25/08) — résultats

Re-test de toutes les fonctionnalités sur la VM, comme exigé. **33 vérifications**, dont 31 PASS directs et **2 vrais bugs découverts, root-causés, corrigés, déployés et réprouvés** :

### ✅ Ce qui est repassé au vert sans modification
- Santé des 15 conteneurs ; catalogues publics (produits, événements, sondages, résultats élections) sans token
- Auth : login, register, refresh, 401 sans token partout ailleurs
- Séances/convocations : 403 adhérent, création ADMIN, filtres staff ; appels LiveKit E2E complet rejoué OK
- Billetterie : purchase invalide 400, quotas ; boutique : variante inconnue 400 propre
- Élections : résultats publics 200 sans token
- Gamification, notifications, content : comportements conformes

### 🐛 Bug #1 — Newsletter publique cassée (401 gateway) — CORRIGÉ & DÉPLOYÉ (a47553d)
- **Symptôme** : `POST /api/notification/newsletter/subscribe` sans JWT → 401 à la gateway alors que le service autorisait l'anonyme. Le footer était cassé pour tout visiteur non connecté.
- **Cause** : le filtre JWT global de la gateway n'avait pas de dérogation pour ce chemin.
- **Correctif** : dérogation gateway + test unitaire (la newsletter passe, broadcast reste 401). Suite gateway 6/6.
- **Preuve prod** : subscribe 201, email invalide 400, broadcast 401.

### 🐛 Bug #2 — Chat/messagerie cassés en prod (403) — CORRIGÉ & DÉPLOYÉ (7e326da + 9fd0010)
- **Symptôme** : inbox et team-chat REST 403, handshake SockJS `/ws/team-chat/info` 403, WARN « Roster indisponible » en boucle dans communication-service. Toute la communication temps réel était morte en prod depuis la migration thématique.
- **Causes racines (2 couches)** :
  1. **Nommage variable d'environnement** : docker-compose exporte `WYDAD_INTERNAL_SECRET` mais les application.yml de sports et communication lisaient `${INTERNAL_SECRET:}` (les 8 autres services lisaient le bon nom) → propriété vide → signatures et validations internes avec un secret vide.
  2. **Dérogations Spring Security perdues** : après la migration, `anyRequest().authenticated()` sans exception pour `/api/sports/internal/**` (appel interne anonyme rejeté AVANT le validateur de secret du contrôleur) ni pour `/ws/team-chat/**` (handshake SockJS ne peut pas porter de JWT — il est validé à la frame CONNECT par TeamChatAuthInterceptor).
- **Correctifs** : alignement du placeholder sur `WYDAD_INTERNAL_SECRET` ; `permitAll` ciblés (pattern notification-service) ; **4 nouveaux tests de non-régression** (InternalRosterAccessTest ×4 : bon secret sans identité gateway → 200/404, mauvais/sans secret → 403 ; WsHandshakeAccessTest ×1 : /info anonyme jamais 403).
- **Preuve prod** : roster bon secret → 404 (utilisateur sans fiche = nominal), mauvais secret → 403 ; `/info` → 200 ; inbox et messages team-chat → 200 ; **E2E WS complet rejoué PASS** (frame MESSAGE reçue par le joueur 9, persistance REST confirmée). Données de test nettoyées après preuve.

> Enseignement process : les faux positifs initiaux du re-test (routes GET inexistantes, corps JSON mal formés) ont tous été écartés avant de conclure — seuls ces 2 bugs étaient réels, et les 2 étaient des régressions de la migration thématique invisible tant qu'on ne relançait pas les parcours E2E bout-en-bout.

---

## 7. Reste à faire

**Avant ouverture publique (bloquant)** :
- [ ] Changer le mot de passe seed admin
- [ ] Régénérer le secret LiveKit + la clé API Cloudinary (exposés par le passé)
- [ ] Nettoyer les comptes test résiduels (auth_db ids 5, 8, 9, 10)

**Qualité** :
- [ ] JaCoCo ≥70 % sur le parent pom (seuils renforcés auth/sports)
- [ ] Étendre les specs Angular aux composants critiques (login, guards, join() LiveKit, formulaires, élections)
- [ ] Unifier les méthodes dupliquées du front (upgradeMembership/changeUserRole/toggleUserActive)
- [ ] Terminer l'unification du thème clair (~18 pages sombres restantes)

**Fonctionnel (roadmap communauté)** : voir RESTE-A-DEVELOPPER.md

---

*Rapport généré le 25/08/2026 — Wydad Digital · commits de la campagne : a47553d (newsletter), 7e326da (secret interne), 9fd0010 (dérogations security + tests non-régression)*
