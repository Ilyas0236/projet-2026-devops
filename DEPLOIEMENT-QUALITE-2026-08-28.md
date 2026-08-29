# Déploiement — Release qualité 2026-08-28

**Périmètre** : 6 chantiers du plan « site de qualité » (qualité back,
front cloche, targetUrl dynamique, notifications tous rôles, email mock
documenté, NPE SubscriptionResponse). Voir `plans/crispy-gliding-mist.md`
pour le plan complet.

**Volumes** :
- 7 services back modifiés (auth, ticket, content, election, notification,
  + helper PlayerOrStaffUrlResolver dupliqué dans sports & communication)
- 3 services front (public-layout, espace-layout, admin-layout +
  auth.guard + composant notification-bell nouveau)
- 2 scripts (audit-quality-final.sh, test-email-mock.sh)

**Builds locaux vérifiés** (avant commit) :
- `auth-service`      ✓ (filter `?roles=…` sur /internal/recipients)
- `ticket-service`    ✓ (broadcast ciblé supporters à createEvent)
- `content-service`   ✓ (broadcast ciblé journalistes à createArticle)
- `election-service`  ✓ (broadcast global à closeAndPublish)
- `notification-service` ✓ (broadcastTargeted + /internal/broadcast-targeted)
- `wydad-frontend`    ✓ (cloak + bundle dans dist/)

---

## Procédure VM

### 1. Pull + recréation des images Docker

```bash
cd ~/wydad-digital-parent
git pull
```

### 2. Build des images back (services modifiés)

```bash
mvn -DskipTests -Dmaven.test.skip=true package \
  -pl auth-service,notification-service,ticket-service,content-service,election-service \
  -am
docker compose build --no-cache \
  auth-service notification-service ticket-service content-service \
  election-service sports-service communication-service
```

### 3. Build du front (Dockerfile multi-stage, pas dans reactor Maven)

```bash
cd wydad-frontend
npm ci
npm run build
cd ..
docker compose build --no-cache wydad-frontend
```

### 4. SQL pre-back : état initial `plan_id IS NULL`

```bash
docker exec -i auth-db psql -U wydad -d auth_db \
  -c "SELECT COUNT(*) AS nb_sans_plan FROM user_subscriptions WHERE plan_id IS NULL;"
```

Note : `SubscriptionPlanSeeder` applique le backfill au démarrage
(`@PostConstruct` / `ApplicationReadyEvent`). Le seeder est idempotent.

### 5. Restart des services modifiés (sans dépendances)

```bash
docker compose up -d --no-deps \
  auth-service notification-service ticket-service content-service \
  election-service sports-service communication-service wydad-frontend
```

### 6. SQL post-back : vérifier le backfill

```bash
docker exec -i auth-db psql -U wydad -d auth_db \
  -c "SELECT COUNT(*) AS nb_sans_plan FROM user_subscriptions WHERE plan_id IS NULL;"
# attendu : 0
```

### 7. Tests E2E

```bash
bash scripts/audit-quality-final.sh
# attendu : "── Audit qualité final : OK ──"
```

Le script T1..T9 vérifie :
- T1 supporter cloche, T2 joueur dashboard + bundle bell
- T3 entraîneur convoque, T4 journaliste notifié, T5 parent cloche
- T6 président cloche, T7 admin createEvent → supporter notifié
- T8 auth.guard returnUrl, T9 NPE SubscriptionResponse

### 8. Vérifications manuelles

| Vérification | Attendu |
|---|---|
| `curl -s http://localhost:8086/actuator/health` | `{"status":"UP"}` |
| `docker logs notification-service --tail 50` | trace des broadcasts ciblés (📢 Broadcast ciblé : N notification(s)…) |
| Login supporter sur le front | cloche visible topbar publique |
| Admin → créer un match J+15 | supporters (USER/ADHERENT) reçoivent notif cloche |
| Admin → publier un article | journalistes reçoivent notif cloche |
| `git checkout auth.guard.ts` | retour à la version sans `returnUrl` (rollback sûr) |

---

## Risques & rollback

- **NPE SubscriptionResponse** : déjà déployé et testé en 26/08, aucun
  risque résiduel.
- **Backfill `plan_id`** : UPDATE SQL idempotent, ne touche pas les lignes
  déjà remplies. Rollback = revert du commit + `docker compose up -d
  --no-deps --force-recreate auth-service`.
- **Broadcast ciblé** : nouvel endpoint `/internal/broadcast-targeted` +
  méthode orchestrateur `broadcastTargeted`. Les anciens flux
  (`/internal/send`, `/internal/broadcast`) restent intacts.
- **Cloche polling 60s** : à observer côté front. Si CPU/RAM gonfle
  (improbable sur 7 rôles × polling 60s), passer le polling à 120s dans
  `notification-bell.component.ts`.
- **E.3 parent** : NON implémenté (cf. `memory/plan-e3-parent-blocker.md`).
  Pas de régression, juste un manque fonctionnel à confirmer avec le
  propriétaire.

## Fichiers critiques (référence rapide)

| Fichier | Rôle |
|---|---|
| `auth-service/.../controller/AuthController.java` | Filter `?roles=` sur /internal/recipients |
| `auth-service/.../config/SubscriptionPlanSeeder.java` | Backfill `plan_id` |
| `auth-service/.../dto/subscription/SubscriptionResponse.java` | Fix NPE |
| `auth-service/.../repository/subscription/UserSubscriptionRepository.java` | Requête backfill |
| `notification-service/.../controller/NotificationController.java` | + `/internal/broadcast-targeted` |
| `notification-service/.../service/NotificationOrchestrator.java` | + `broadcastTargeted()` |
| `notification-service/.../dto/NotificationRequest.java` | + `targetUserIds` |
| `notification-service/.../service/EmailService.java` | MOCK documenté + flag |
| `notification-service/src/main/resources/application.yml` | `notification.email.mock: true` |
| `ticket-service/.../service/EventService.java` | Hook broadcast supporters J-30→J+0 |
| `ticket-service/.../client/NotificationClient.java` | + `notifyBroadcastTargeted` |
| `ticket-service/.../client/AuthClient.java` | + `fetchActiveSupporters` |
| `content-service/.../service/ContentService.java` | Hook broadcast journalistes |
| `content-service/.../client/NotificationClient.java` | (nouveau) |
| `content-service/.../client/AuthClient.java` | (nouveau) |
| `content-service/src/main/resources/application.yml` | + `notification-service-uri` |
| `election-service/.../client/NotificationClient.java` | + `notifyBroadcast` |
| `election-service/.../service/ElectionService.java` | fix broadcast global |
| `wydad-frontend/.../components/notification-bell/notification-bell.component.ts` | (nouveau) cloche |
| `wydad-frontend/.../services/notification.service.ts` | (nouveau) front service |
| `wydad-frontend/.../layouts/{public,espace,admin}-layout/*.component.html` | + cloche |
| `wydad-frontend/.../guards/auth.guard.ts` | + `returnUrl: state.url` |
| `sports-service/.../util/PlayerOrStaffUrlResolver.java` | (nouveau) helper targetUrl |
| `communication-service/.../util/PlayerOrStaffUrlResolver.java` | (nouveau) helper dupliqué |
| `sports-service/.../service/{MatchConvocation,Medical,PlayerSpace,ScheduledCall,Session}Service.java` | refactor targetUrl |
| `communication-service/.../service/{Messaging,TeamChat}Service.java` | refactor targetUrl |
| `scripts/audit-quality-final.sh` | (nouveau) E2E T1..T9 |
| `scripts/test-email-mock.sh` | (nouveau) test mock email |
