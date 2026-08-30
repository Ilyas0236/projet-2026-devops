---
name: abonnement-admin-photo-2026-08-30
description: B.12 refonte /abonnement + home : 100% piloté par admin (photo carte + privileges via Cloudinary)
metadata:
  type: project
---

Refonte B.12 « Abonnements Wydad » déployée 30/08/2026, E2E 9/9 vert sur VM.
L'admin peut désormais gérer **100% du contenu des cartes** depuis
`/admin/subscription-plans` : nom, prix régulier, prix adhérent, **photo
de carte** (upload Cloudinary, folder `subscription-cards/<code>`, type
`upload` public, max 5 Mo), privileges (`benefits` texte libre une ligne
par privilege, activable/désactivable), ordre d'affichage.

**Back** (commits ef82d9a, ba901a2) :
- `SubscriptionPlan.cardImageUrl` (VARCHAR 512) — créé par `ddl-auto=update`
- `SubscriptionPlanResponse.cardImageUrl` + `from()`
- `CloudinaryService.uploadPlanCardImage(file, planCode)` — mode dégradé
  `local:plan:<code>` si pas de clés configurées
- `AdminSubscriptionPlanController` POST/DELETE `/{id}/card-image`
  (multipart, @PreAuthorize ADMIN au niveau classe)
- **Route gateway dédiée** `/api/admin/subscription-plans/**` (manquait —
  bug d'infrastructure qui affectait déjà le CRUD existant !)

**Front** (commits ef82d9a, ac3f606) :
- `api.service.ts` : `adminUploadPlanCardImage(id, file)` /
  `adminDeletePlanCardImage(id)` (FormData comme `uploadMyPhoto`)
- `admin-subscription-plans.component.{ts,html}` : preview + upload + remove
  dans le modal d'édition
- `abonnement.component.html` : refonte des cartes, suppression des
  fallbacks `*ngIf="!plan.benefits"`
- `home.component.html` : idem section « Mes Abonnements »
- Sous-titres de page purgés (« 15 matchs à domicile », « carte PDF
  instantanément ») — le sous-titre n'est plus une donnée métier statique

**E2E** : `scripts/test-abonnement-admin.sh` — login admin + GET public +
POST upload + GET public reflète + DELETE + GET public propre + check
fallbacks côté visiteurs.

**Why:** PIEGE A RETENIR — la gateway n'avait pas de route pour
`/api/admin/**` ; le CRUD existant listerait 404 sans la route dédiée.
Tout panel admin déclaré avec `@RequestMapping("/api/admin/...")` côté
back doit être routé dans `api-gateway/src/main/resources/application.yml`.

**How to apply:** Pour tout futur `@RequestMapping("/api/admin/...")` :
ajouter une entrée `id: auth-admin-<feature>` pointant vers
`${AUTH_SERVICE_URI:http://localhost:8081}` avec predicate
`Path=/api/admin/<feature>/**`. Re-tester avec `mvn clean package`
(⚠️ pas juste `package`, sinon Maven ne re-copie pas les resources YAML).

Déploiement : commits ef82d9a, 6687ab2, ba901a2, 20c5316, 0e1c238, ac3f606.
Pièges franchis : mot de passe admin changé le 25/08 (lit .env maintenant),
ddl-auto=update colonne auto-créée au restart auth-service, le `--no-cache`
est obligatoire pour le front (cache ignore dist/), URL du contrôleur
`/api/admin/...` PAS `/api/auth/admin/...`.

Lié à [[audit-complet-2026-08-26]], [[docker-only-deployment]],
[[azure-deployment]], [[b28-front-deploy-2026-08-27]].
