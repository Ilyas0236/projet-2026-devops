# Wydad Digital — Récapitulatif Tests & Fonctionnalités

> Document de référence : état complet de la plateforme (backend, frontend, DevOps, déploiement) au **25/08/2026**.
> Machine de prod : VM Azure B2s_v2 Spain Central `158.158.74.169` — tout en Docker, pas de Kubernetes.

---

## 1. Architecture — un microservice = une responsabilité

| Service | Port | Base | Thème |
|---|---|---|---|
| api-gateway | 8080 | — | Unique porte d'entrée publique, routage + blocage endpoints internes |
| auth-service | 8081 | auth_db | Comptes, JWT, rôles, KYC Cloudinary, validation comptes par documents |
| content-service | 8082 | content_db | Actualités, pages publiques du club |
| payment-service | 8083 | payment_db | Paiements (simulation), référencé par shop/ticket |
| shop-service | 8084 | shop_db | Boutique officielle |
| ticket-service | 8085 | ticket_db | Billets VIP + QR (4/adhérent), scan sécurisé |
| notification-service | 8086 | notification_db | Notifications in-app centralisées (`/internal/send`) |
| sports-service | 8087 | sports_db | Domaine sportif : joueurs/staff, convocations, séances, stats, médias, academy, appels LiveKit, API roster interne |
| gamification-service | 8088 | wydad_gamification | Points/badges fans |
| election-service | 8089 | elections_db | Gouvernance : élections du président + sondages |
| communication-service | 8090 | communication_db | Messagerie privée, annonces staff, chat de groupe, WebSocket STOMP |

Squelette identique dans chaque service : `model/ repository/ dto/ filter/ (X-User-* ThreadLocal) client/ config/ service/ controller/ (+GlobalExceptionHandler)` — même Dockerfile (temurin21-jre-alpine, non-root).

Découplage service↔service :
- Roster via API interne sports `/api/sports/internal/roster/**` protégée `X-Internal-Secret` (SHA-256 comparaison à temps constant).
- Notifications via `/api/notification/internal/send`, best-effort.
- Endpoints internes bloqués côté gateway (routes `block-*-internal` en tête).

---

## 2. Backend — tests automatisés (JUnit 5, ISTQB)

Techniques utilisées : transitions d'état, tables de décision, valeurs aux limites, partitions d'équivalence.

| Service | Tests | Couverture principale |
|---|---:|---|
| auth-service | 45 | login isActive, refresh token, refus de compte avec motif, KYC, rôles ENTRAINEUR/JOURNALISTE/PRESIDENT |
| content-service | 56 | CRUD actualités, permissions publication |
| payment-service | 6 | cycle de paiement, transitions d'état |
| shop-service | 15 | commandes, stock, décisions achat |
| ticket-service | 28 | quota 4 billets VIP, QR, sécurité scan (TicketScanSecurityTest) |
| notification-service | 25 | envoi interne protégé, lecture par destinataire |
| sports-service | 51 | convocations, sessions (création ADMIN only), stats matchs, médias (limites upload), médical, ownership joueur, appels programmés LiveKit (14) |
| gamification-service | 15 | attribution points, anti-triche |
| election-service | 24 | cycle élection complet (16), accès public résultats vs vote authentifié (PollPublicAccessTest 4 + PollSecurityTest 4) |
| communication-service | 15 | messagerie privée joueur↔staff par catégorie (MessagingSecurityTest 5), chat de groupe REST (TeamChatSecurityTest 6 : hors catégorie 403, sans fiche roster 403, admin superviseur, limites 500 car.), canal STOMP (TeamChatWsControllerTest 4) |
| **Total backend** | **280** | |

⚠️ Note environnement : Maven ≤ 3.8 exécutait surefire 2.12.4 (ignorait JUnit 5 → « Tests run: 0 » silencieux). Corrigé : compiler 3.13.0 + surefire 3.2.5 épinglés dans le parent pom.

### Vérification locale ET sur VM Azure
Les suites sports (51), election (24) et communication (15) sont passées **en vert localement et sur la VM** après les correctifs du 25/08.

---

## 3. Frontend (Angular standalone)

- Build production OK (CI `frontend-build` + image Docker nginx).
- Suites de specs existantes (7 fichiers) : `my-calls.component.spec.ts`, `team-chat.component.spec.ts`, `vip-tickets.spec.ts`, `api.service.spec.ts`, `auth.service.spec.ts`, `confirm.service.spec.ts`, `toast.service.spec.ts`.
- Cohérence URLs : sondages migrés vers `/polls/**` (election-service) ; messagerie/chat conservent les chemins historiques `/sports/messaging|team-chat` routés vers communication-service par la gateway (aucune rupture utilisateur).

### Reste à faire frontend (priorisé)
1. Étendre les specs Angular aux composants critiques : login, register, profil KYC, admin-demandes, guards, intercepteur JWT, join() LiveKit, schedule-call-form (+ elections/mes-elections/admin-elections).

---

## 3.bis Élections du président (B.8) — TERMINÉE 25/08

**Frontend livré et déployé** (commit d5fb499) :
- `/elections` — page publique des résultats (thème clair) : gagnant surligné avec %, barres par candidat (`[style.width.%]`), état vide vers `/sondages`, skeleton de chargement. Accessible SANS connexion (exigence B.8).
- `/mes-elections` — espace adhérent (garde auth) : vote en un clic, boutons désactivés après vote (`myVoteIndex`), anti-double-clic, confirmation verte + lien résultats.
- `/admin/elections` — UI admin (thème sombre cohérente avec admin-sondages) : création session (titre + datetime-local), ajout/retrait candidats (nom, photo URL, présentation ≤1000), clôture « & publier » avec ConfirmService.
- Navigation : lien public navbar+footer « Élections », entrée menu admin (le lien `/admin/sondages` manquant a aussi été ajouté — page jusque-là inatteignable).
- 8 méthodes ApiService nouvelles ; build prod vert, 38/38 specs front.

**Parcours E2E prouvé EN PROD sur la VM (25/08)** :
1. `POST /api/auth/register` → compte adhérent jetable (id11, rôle ADHERENT).
2. Admin : création élection id1 (« Élection Présidentielle WAC 2026 ») + 2 candidats.
3. Membre `GET /api/elections/open` → liste avec candidats ✅
4. Membre `POST /api/elections/1/vote {candidateId:1}` → accepté ✅
5. Re-vote candidat 2 → **409** « Vous avez déjà voté pour cette élection » ✅
6. Admin `POST /api/elections/1/close` → status CLOSED, published=true, winnerCandidateId=1 ✅
7. `GET /api/elections/published/latest` **SANS token** → 200 avec totalVotes=1, results=[1,0], percentages=[100,0] (exigence B.8 : résultats publics) ✅
8. Page publique `http://158.158.74.169:4200/elections` → HTTP 200 ✅
9. Nettoyage complet : votes/candidats/élection/compte test supprimés (elections_db + auth_db), mot de passe jetable effacé du serveur.

Piège Angular documenté : un binding `[class.bg-wydad-red/10]` (slash dans le nom de classe) casse le parseur de template avec des erreurs NG5002 trompeuses — utiliser `[style.background]` ou une classe sans slash.

---

## 4. E2E prod (scripts dans `scripts/`)

| Script | Preuve attendue | Dernier statut |
|---|---|---|
| `e2e-ws.sh` + `e2e-ws-frames.py` | Deux sessions SockJS, frame MESSAGE reçue en temps réel + persistance REST | ✅ Prouvé Phase 4 |
| `e2e-calls.sh` | Programmer appel → agenda → jeton LiveKit → annulation 400 | ✅ Prouvé Phase 5 |
| Routing gateway (25/08) | `/api/polls/active`, `/api/elections/published/**`, `/api/shop/products`, `/api/ticket/events*` publics sans JWT ; messaging/team-chat sans JWT → 401 (routés vers communication-service) ; `/api/sports/internal/**` et `/api/communication/internal/**` bloqués depuis l'extérieur (exigence B.8 + pages publiques boutique/billetterie) | ✅ Vérifié sur la VM après rebuild gateway (2 correctifs : sondages/résultats puis catalogue shop/ticket — commit 5d69380) |
| Gateway unitaires (`InternalRoutesBlockedTest` 3 + `PublicCatalogAccessTest` 2) | Lecture publique jamais 401 sans token sur les 6 chemins publics ; POST catalogue toujours 401 ; endpoints internes bloqués même avec JWT valide | ✅ 5/5 verts local |

### Audit de cohérence backend↔frontend (25/08)
Audit exhaustif : chaque URL du frontend vérifiée contre routes gateway + contrôleurs backend + rôles.
**Résultat : 4 écarts corrigés le jour même** — commentaires WS périmés, route académie sans garde PARENT/ADMIN,
formulaire d'appels visible au STAFF simple (403 à la soumission), méthode morte `getAllJoueurs`.
Un point de maintenance restant : duplication `upgradeMembership/changeUserRole/toggleUserActive` entre
api.service.ts et auth.service.ts (à unifier lors d'un futur refactor front).

Identifiants E2E : uniquement via variables d'environnement / `.env` serveur — jamais dans le dépôt ni le chat.

---

## 5. DevOps / CI-CD

`.github/workflows/ci.yml` — pour CHACUN des 11 services backend :
1. `build-and-test` : parent pom install puis `mvn clean package --file <svc>/pom.xml`
2. Upload artifact JAR
3. Job docker dédié : buildx + push Docker Hub `<user>/wydad-<svc>:latest` et `:<sha>` + cache GHA

Plus `frontend-build` + `docker-frontend`. Déclenchement : push et PR sur `main`.

Déploiement : boucle `git pull && mvn package && docker compose up -d --build` sur la VM, journalisée dans `DEPLOIEMENT-AZURE.md`.

Sauvegardes : `scripts/backup-db.sh` (pg_dump gzip, rétention 7 jours, `~/backups`) + `scripts/install-backup-cron.sh` (cron quotidien 04h17).

Secrets : uniquement `.env` serveur (chmod 600) + gestionnaire de mots de passe. Jamais dans git ni le chat.
À faire avant ouverture publique : changer le mot de passe seed admin, régénérer secret LiveKit et clé API Cloudinary (exposés par le passé).

---

## 6. Sécurité (durcissements vérifiés par tests)

- Login refusé si compte `isActive=false` ; statuts EN_ATTENTE/VALIDE/REFUSE avec circuit de validation par documents.
- Refresh token rotatif ; endpoints internes injoignables depuis l'extérieur (401/403/404 selon route).
- Chat : adhésion déduite du roster (impossible de s'inviter dans un groupe) ; JWT validé à la frame CONNECT ; casse des en-têtes STOMP maîtrisée (« Authorization »).
- Scan de billets sécurisé (TicketScanSecurityTest) ; quotas billets serveur.

---

## 7. Historique des phases

- **Phase 0** — Rôles & statuts (déployée, ALTER TABLE manuel requis en prod pour CHECK d'enum)
- **Phase 2** — Billets VIP + QR
- **Phase 3** — Convocations & médias (E2E prouvé ; comptes test ids 8-10 à nettoyer avant prod)
- **Phase 4** — Messagerie WebSocket STOMP (E2E prouvé en prod ; bug ThreadLocal corrigé a707942)
- **Phase 5** — Appels vidéo/vocaux LiveKit (E2E prod prouvé ; pièges adaptiveStream/accessToken/Dockerfile multi-stage)
- **Correctifs déploiement 25/08** — surefire épinglé dans le parent (Maven ≤3.8 exécutait 0 test) ; `elections_db`/`communication_db` ajoutées aux init-scripts ; clé compose dupliquée ; lecture publique polls/résultats **puis catalogue shop/ticket** côté gateway (commit 5d69380, matrice routage validée sur VM)

## 8. Prochaines étapes
1. JaCoCo ≥70% + extension specs Angular composants critiques (+ elections)
2. Refactor thématique content/sports ; unification des méthodes dupliquées du front
