# Reste à développer — Wydad Digital

> Généré le 2026-08-25, après clôture de la Phase 4.
> Source de vérité : `ROADMAP-DEPLOIEMENT.md` + audit du 2026-08-24.

## État d'avancement global

| Phase | Contenu | État |
|---|---|---|
| Vague 0 | Sécurité S1-S6 (login isActive, refresh token, membershipLevel) | ✅ Terminé |
| Design | Migration des 21 vues sombres vers thème clair blanc/rouge | ✅ Terminé |
| Phase 0 | Socle rôles & statuts (ENTRAINEUR/JOURNALISTE/PRESIDENT, EN_ATTENTE/VALIDE/REFUSE) | ✅ Terminé |
| Phase 1 | Justificatifs KYC via Cloudinary + consultation admin (URL signée) | ✅ Terminé |
| Phase 1 bis / ter | Finalisation validation comptes par documents | ✅ Terminé |
| Phase 2 | Billets VIP joueurs (PDF + QR) | ✅ Terminé |
| Phase 3 | Convocations & médias tactiques Cloudinary + boîte joueur unifiée | ✅ Terminé |
| **Phase 4** | **Messagerie groupe joueurs WebSocket/STOMP** | ✅ **Terminée 25/08** |
| **Phase 5** | **Appels vidéo/vocaux programmés (LiveKit)** | ✅ **Terminée 25/08** — E2E prod prouvé |
| Phase 5 bis | Espace Président | ⬜ À faire |
| Phase 6 | Tests ISTQB + automatisation (JaCoCo, Testcontainers, Playwright) | 🔄 En cours |
| Phase 7 | Finitions avant mise en production | 🔄 Démarrée (durcissement compose + scripts backup prêts) |

**Coût Azure vérifié ce jour : 0,48 $ dépensés sur 100 $ — reste ≈ 99,52 $.**

---

## Phase 5 — Appels vidéo/vocaux programmés ✅ (terminée 25/08)

Terminée et prouvée E2E en prod (commits 7624456 → f4b52c6). Backend : `ScheduledCallService` + `LiveKitTokenService`
(jetons JWT HS256 room/contrôlés serveur), contrôle d'accès coach/président/joueur, notifications in-app.
Frontend : agenda appels (`my-calls`), formulaire programmation (`schedule-call-form`), rejoindre via SDK `livekit-client`.
Script E2E prod : `scripts/` (programmer → jeton → annuler 400).

**Reste de Phase 5 (tests)** : scénario ISTQB « accès STANDARD refusé aux appels » à expliciter
(membership STANDARD vs PREMIUM au moment du joinToken) — voir Phase 6.

---

## Phase 5 bis — Espace Président ⬜

Aucune dépendance externe — recommandée pour démarrer demain.

1. Interface dédiée rôle PRESIDENT (socle layout admin existant, thème clair blanc/rouge).
2. Messagerie président → agents (staff administratif) : horodatée, persistée, réutilise le modèle `team_messages` ou table dédiée `president_messages`.
3. Reçus PDF salaires/primes :
   - Génération PDF OpenPDF (déjà utilisé pour les billets VIP Phase 2 — même stack).
   - Upload Cloudinary folder privé séparé `president-receipts/` (type authenticated, URL signée 1 h — même modèle que KYC/médias).
   - Visibilité : le joueur voit SES reçus uniquement (ownership strict + test 403).
4. Lancement de réunions vidéo : réutilise Phase 5 quand elle existe.
5. Tests : création message, génération/upload reçu, ownership reçus, guards rôles.

---

## Phase 6 — Tests (ISTQB + automatisation) 🔄

Déjà fait (audit 25/08) : 43 classes de test backend, ~251 @Test. Scénarios ISTQB déjà couverts :
billet QR re-scanné (`TicketServiceTest`), message vide chat → 400 (`TeamChatSecurityTest`, HTTP+WS),
refus compte avec motif (`AuthServiceValidationTest`, unitaire), non-convié ADHERENT jeton refusé
(`ScheduledCallServiceTest`). Infra Karma front : 7 specs sérieux (~598 lignes).

### Restant
1. **JaCoCo ≥ 70 %** : plugin absent des 10 poms → à ajouter au parent + seuils `check` auth/sports.
2. **Testcontainers PostgreSQL** : dépendance déclarée dans auth-service mais jamais utilisée ;
   H2 `MODE=PostgreSQL` partout. Priorité : un test d'intégration Postgres réel sur auth-service (CHECK enums).
3. **Scénarios ISTQB manquants / à renforcer** :
   - ~~Upload > 25 Mo → 413~~ ✅ 25/08 `MediaUploadLimitTest` (4 tests : 24 Mo OK, 26 Mo → 413,
     staff sans fiche → 403, anonyme refusé). *Au passage* : le garde-fou applicatif
     `MediaStorageService` lançait `IllegalArgumentException` (→400) ; corrigé en
     `MaxUploadSizeExceededException` → handler dédié → 413.
   - ~~Billet CANCELLED scanné + QR inconnu + endpoint HTTP scanner~~ ✅ 25/08
     `TicketScanSecurityTest` (6 tests, MockMvc H2). *Au passage* : le handler générique
     `Exception` du ticket-service avalait `MethodArgumentNotValidException`/JSON malformé
     (500 au lieu de 400) — handlers dédiés ajoutés.
   - ~~Refus compte avec motif : monter en MockMvc~~ ✅ 25/08 `AccountRefusalTest` (4 tests).
     *Défauts réels découverts et corrigés* : (1) `{}` désérialisé en chaîne littérale `"{}"`
     passait @NotBlank sur `@RequestBody String` → corps typé `RefuseAccountRequest` DTO +
     front aligné (`{ motif }`) ; (2) `@Validated` manquant sur AuthController.
   - Accès STANDARD aux appels explicite (membership STANDARD vs PREMIUM au joinToken) —
     couvert indirectement par `presidentPeutCiblerLesAdherentsPremium` (ROUGE/OR exclus) ;
     test direct à évaluer.
4. **Frontend** : specs manquantes critiques = login, register, profil KYC, admin-demandes,
   guards (auth/admin/role), intercepteur JWT, `join()` LiveKit, `schedule-call-form`.
   CI GitHub Actions : les tests Angular ne tournent jamais → ajouter job `ng test --watch=false --browsers=ChromeHeadless --code-coverage`.
5. **Playwright E2E navigateur** sur parcours critiques (inscription→validation→connexion ; convocation→accusé ;
   chat 2 onglets ; billet VIP téléchargé + QR scanné).

---

## Phase 7 — Finitions avant mise en production ⬜

1. Tests bout-en-bout de chaque parcours (création compte → validation → usage réel).
2. Relecture sécurité : guards JWT par rôle sur CHAQUE nouvel endpoint ajouté depuis l'audit (chat WS, médias, convocations batch, appels).
3. Nettoyage données de test AVANT vraie prod :
   - comptes auth_db ids **8–10** (coach.p3, joueur.p3a, joueur.p3b) + fake.coach id 7 ;
   - lignes sports_db : players, staff, sessions, convocations, player_documents, team_messages ;
   - documents KYC de démonstration sur Cloudinary folder `kyc-documents/`.
4. Changer le mot de passe admin seed (`admin@wac.ma`) — **toujours en attente ⚠️**

---

## Checklist prod (blocage ouverture publique)

| # | Action | Statut |
|---|---|---|
| 1 | Changer mot de passe admin seed | ❌ En attente |
| 2 | Supprimer pgAdmin du compose prod ; fermer ports hôte Postgres/Redis (5433/6379) | ❌ Exposés actuellement |
| 3 | HTTPS Caddy + domaine (eu.org/DuckDNS gratuit possible) ; CORS_ALLOWED_ORIGINS vers domaine final | ❌ |
| 4 | Sauvegardes : cron quotidien `pg_dumpall` (scripts/backup-db.sh + install-backup-cron.sh prêts, à installer sur la VM) + copie hors serveur hebdomadaire | 🔄 Scripts prêts |
| 5 | Monitoring minimal : cron `docker compose ps` + alerte email conteneur unhealthy | ❌ |
| 6 | Régénérer la clé API Cloudinary (secret passé par chat) | ❌ À faire en vrai prod |

---

## Option migration Oracle Cloud Always Free (à terme)

Oracle A1 Flex : 4 OCPU / 24 Go RAM **gratuit à vie** vs ~38–40 $/mois Azure.
Recommandation : rester sur Azure pendant le développement/démo (crédit large), basculer sur Oracle pour la pérennité.

Étapes (détaillées dans ROADMAP-DEPLOIEMENT.md §4.2) : créer compte Oracle → instance A1 4/24 Ubuntu → ports Security List + ufw → build arm64 sur place → transférer `.env` (recréer secrets) → `pg_dumpall` migration → DNS.

## 💡 Économiser le crédit Azure

La VM coûte **~0,045 $/h ≈ 1,08 $/jour**. Options concrètes :

1. **Deallocate la VM quand tu ne développes pas** (le plus efficace) :
   ```
   az vm deallocate -g <groupe-ressources> -n <nom-vm> --no-wait
   ```
   → 0 $ pendant l'arrêt (l'IP publique reste réservée si elle est Standard/SKU statique ; vérifier qu'elle n'est pas Basic dynamique, sinon elle change à chaque démarrage — dans ce cas noter l'IP ou passer en statique ~0,004 $/h).
   Redémarrage : `az vm start ...` (~2 min).
2. **Auto-shutdown quotidien** (portail Azure → VM → Operations → Auto-shutdown) : ex. arrêt automatique 01:00, tu la rallumes quand tu travailles. Économie ~8 h/nuit = ~35 % du coût sans rien faire.
3. Ne PAS downgrader B2s_v2 : les 2 vCPU/8 Go servent aux builds mvn sur la VM ; un downgrade ralentirait la boucle dev→deploy.
4. La bande passante sortante est quasi gratuite à ton usage (0,00004 $ ce mois-ci).

Avec auto-shutdown nocturne + deallocate manuel les jours sans dev : **la durée de vie passe de ~2,5 mois à 4–6 mois** de crédit.
