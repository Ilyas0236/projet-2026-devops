# 🧪 RAPPORT DE TESTS COMPLET — Wydad Digital

**Date :** 26–27 août 2026 · **Environnement :** PRODUCTION VM Azure `158.158.74.169` (B2s_v2, Docker-only)
**Périmètre :** tout ce qui est développé — 14 conteneurs, 7 profils de connexion, toutes les fonctionnalités admin, tous les services métier, les 12 routes frontend.
**Verdict final : ✅ AUDIT VERT — 92/92 PASS, 0 FAIL** (script rejouable : `scripts/audit-complet.sh`)

---

## 1. Problèmes trouvés et corrigés (3 bugs réels)

### 🐛 BUG #1 — Le bouton « Valider » admin ne marchait pas depuis le navigateur (BLOQUANT)
- **Symptôme rapporté par le propriétaire :** « quand j'approuve la demande d'inscription il me donne erreur ».
- **Cause racine :** `SecurityConfig.java` d'auth-service autorisait CORS sur GET/POST/PUT/DELETE mais **pas PATCH**. Les validations/refus admin passent en `PATCH /api/auth/admin/accounts/{id}/validate|refuse`. Le navigateur envoie d'abord une requête préliminaire OPTIONS (preflight) qui recevait **403** → la vraie requête n'était jamais envoyée.
- **Pourquoi mes tests curl passaient quand même :** curl n'envoie pas de header `Origin`, donc pas de preflight.
- **Fix :** ajout de `"PATCH", "OPTIONS"` dans `setAllowedMethods` (auth-service). Commit `791a465`, déployé et vérifié (OPTIONS → 200 avec PATCH dans Access-Control-Allow-Methods).

### 🐛 BUG #2 — Leaderboard public inaccessible aux visiteurs
- **Symptôme :** `GET /api/gamification/leaderboard` sans token → **401**, alors que le gamification-service déclare ce GET `permitAll()` (« Défense de titre : classement visible de tous »).
- **Cause :** incohérence gateway/service — le filtre JWT de l'api-gateway n'avait pas de bypass pour cette route.
- **Fix :** bypass aligné sur le pattern existant (content/polls/elections/boutique) dans `JwtAuthenticationFilter.java` : si un token est fourni il est quand même validé et l'identité transmise ; sinon on laisse passer l'anonyme. Commit `26dbb96`. Vérifié : anonyme → **200**.

### 🐛 BUG #3 — Les erreurs de validation renvoyaient 403 au lieu de 400 (tous services)
- **Symptôme :** refus de compte sans motif → **403 générique** au lieu du **400 « Le motif de refus est obligatoire »**. L'utilisateur voyait une erreur d'accès alors que c'est un problème de saisie.
- **Cause :** quand `@Valid` échoue, Spring forward vers `/error` en interne ; ce dispatch ERROR était **re-sécurisé** par Spring Security (`Http403ForbiddenEntryPoint` visible dans les logs) → 403 masquait la vraie cause. Latent dans les **10 microservices**.
- **Fix :** `.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()` ajouté aux 10 SecurityConfig — fix standard Spring Security 6, n'ouvre rien au public (dispatch interne uniquement). Commit `7a8ff53`. Vérifié : refus sans motif → **400** avec message explicite.

## 2. Faux positifs écartés (contrats voulus, pas des bugs)
- **Wallet `/api/payment/balance` refusé à un JOUEUR (403)** : PAR DESIGN. `PaymentController` restreint le porte-monnaie à `ADHERENT/ADMIN` (circuit cotisations adhérents). Le script a été corrigé pour tester JOUEUR→403 **et** ADHERENT→200 (les deux passent).
- **JOURNALISTE non sollicitable sans match réel** : par design (§17) — l'accréditation exige un match du calendrier réel.
- **PRESIDENT non sollicitable à l'inscription** : par design (§B.8) — créé par l'admin, il émerge des élections.

---

## 3. Détail des 92 tests (tous PASS)

### A. Infrastructure — 14/14
Les 14 conteneurs healthy : auth, sports, content, ticket, shop, payment, communication, election, notification, gamification, postgres, redis, api-gateway, frontend.

### B. Authentification — circuit complet par rôle (17 tests)
| Test | Résultat |
|---|---|
| Login ADMIN → JWT valide, `/me` → ADMIN | ✅ |
| Inscription ADHERENT → auto-VALIDE, connexion immédiate OK | ✅ |
| Inscriptions JOUEUR / ENTRAINEUR / STAFF / JOUEUR2 (4 disciplines distinctes) → 202 Accepted SANS tokens, statut EN_ATTENTE | ✅ |
| Création PRESIDENT par l'admin (non sollicitable publiquement) → 201 | ✅ |
| JOURNALISTE + organisme presse + match RÉEL du calendrier → 202 | ✅ |
| Aucun compte EN_ATTENTE ne peut se connecter (5 profils testés) | ✅ |
| Mauvais mot de passe admin → 401 | ✅ |

### C. Fonctionnalités ADMIN (12 tests)
- File d'attente `GET /admin/accounts/pending` liste les demandes ✅
- Validation des 5 demandes via `PATCH validate` → 200 chacune ✅
- Compte PRESIDENT admin-created bien VALIDE ✅
- Login post-validation pour les 5 rôles avec bon rôle dans `/me` (JOUEUR, ENTRAINEUR, STAFF, PRESIDENT, JOURNALISTE) ✅
- Refus SANS authentification → 401 ✅ · **SANS motif → 400** (bug #3 corrigé) ✅ · AVEC motif → 200 ✅
- Motif de refus persisté en base ✅ · Compte REFUSE ne peut plus se connecter ✅
- Changement de rôle (`changeUserRole`) → 200 ✅ · Création utilisateur STAFF par admin → 201 ✅

### D. Sécurité & anti-fraude (3 tests)
- Endpoints admin refusés aux rôles non-admin (JOUEUR → 403, ENTRAINEUR → 403) ✅
- **Forge d'en-têtes X-User-Role: ADMIN ignorée** (la gateway nettoie toujours les headers d'identité client) ✅

### E. Sports — espaces & isolation (7 tests)
- `players/filter` accessible admin, refusé JOUEUR ✅
- Convocations match : ENTRAINEUR 403 (sans fiche staff rattachée — honnête), JOUEUR 200 ✅
- Espace joueur (`my-space/presence`, `my-space/stats`) : JOUEUR → 200, ENTRAINEUR → 403 ✅

### F. Contenu — matchs & articles (4 tests)
- Création match admin (FOOTBALL U17 + logo) → id réel ✅
- Suppression match refusée à un JOUEUR ✅
- Articles publics consultables, création réservée admin ✅

### G. Billetterie (3 tests)
- Événements publics ✅ · `tickets/user/{soi}` JOUEUR autorisé ✅ · **tickets/user/{AUTRUI} refusé (anti-IDOR)** ✅

### H. Boutique & Paiement (4 tests)
- Produits publics paginés ✅ · Commande sans token refusée ✅
- Wallet : JOUEUR → 403 (design) / ADHERENT → 200 ✅

### I. Communication (3 tests)
- Newsletter subscribe public → 201 ✅
- Notifications d'un user (admin) → 200 ✅
- **Notification in-app reçue** lors d'une décision de compte (validé/refusé) ✅

### J. Élections & Gamification (4 tests)
- Résultats publiés accessibles sans token ✅
- **Leaderboard public → 200** (bug #2 corrigé) ✅
- Points joueur sans erreur serveur, badges du joueur OK ✅

### K. Frontend SPA (12 routes → 200)
`/`, `/login`, `/register`, `/boutique`, `/billetterie`, `/actualites`, `/elections`, `/profil`, `/admin`, `/espace-entraineur`, `/espace-journaliste`, `/espace-president`

### L. Nettoyage
Tous les comptes/matchs/articles de test supprimés après audit (0 restant en base).

---

## 4. Comment rejouer

```bash
# Sur la VM :
az vm start --resource-group wydad-rg --name wydad-vm   # si désallouée
ssh azureuser@158.158.74.169
cd ~/wydad-digital-parent && source .env
ADMIN_PASSWORD="$ADMIN_SEED_PASSWORD" bash scripts/audit-complet.sh
```
⚠️ Rappel déploiement : les Dockerfiles copient des jars pré-construits → **`mvn package -DskipTests` dans chaque service modifié AVANT `docker compose up -d --build`**.

⚠️ Après deallocate/start de la VM : les conteneurs ne redémarrent pas seuls (pas de restart policy) → `docker compose up -d`.

## 5. Restes connus (non bloquants)
- Comptes de test des phases précédentes (ids 8–10) à purger avant mise en service réelle.
- Pas de restart policy docker (`unless-stopped`) — à activer pour survivre aux deallocate/start sans intervention.
- La campagne E2E historique reste disponible : `TESTS-E2E-CAMPAGNE.md` (37/37) et `scripts/e2e-full-campagne.sh`.
