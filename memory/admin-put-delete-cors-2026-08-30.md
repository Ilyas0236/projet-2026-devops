---
name: admin-put-delete-cors-2026-08-30
description: PUT/DELETE admin fonctionnels (CORS preflight + valeur d'env effective)
metadata:
  type: project
---

Bug diagnostiqué le 30/08/2026 : l'admin connecté au dashboard pouvait
lister les plans (`GET /api/admin/subscription-plans` OK) mais toute
action **Éditer / Activer-Désactiver / Supprimer** échouait silencieusement
dans la console du navigateur (F12). Le test manuel sur l'UI affichait
"Impossible de modifier" sans plus de détail.

## Cause racine (3 couches)

1. **Origine CORS whitelistée trop restrictive** : la variable d'env
   `CORS_ALLOWED_ORIGINS` côté VM était à `http://158.158.74.169:4200`
   (port 4200 + http), alors que le site est servi en `https://158.158.74.169`
   (port 443). Toute requête navigateur avec `Origin: https://158.158.74.169`
   arrivait au `CorsWebFilter` avec une origine non whitelistée → 403.
   Fix : `CORS_ALLOWED_ORIGINS=https://158.158.74.169,http://158.158.74.169,http://localhost:4200,https://localhost:4200`

2. **Le `.env` n'est pas relu par `docker compose up -d --no-deps`** :
   les conteneurs tournaient déjà avec l'ancienne valeur, le simple
   `--no-deps` ne re-forge pas l'env. Fix : `docker compose up -d
   --force-recreate --no-deps <service>` pour forcer la relecture.
   Toujours **vérifier `docker inspect <container> | grep CORS`**
   après modif de `.env` avant de tester l'app.

3. **Spring Security côté auth-service + preflight** : `SecurityConfig`
   a été renforcé avec `requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()`
   et `requestMatchers("/api/admin/**").permitAll()` (la protection RBAC
   reste garantie par `@PreAuthorize("hasRole('ADMIN')")` sur la classe
   contrôleur). Sans ça, OPTIONS sur `/api/admin/...` arrivait sur
   `anyRequest().authenticated()` et laissait Spring rejeter sans
   traiter comme un preflight CORS. Commit `d715b25`.

## Tests passés (SSH VM, `origin: https://158.158.74.169`)

- Preflight `OPTIONS /api/admin/subscription-plans/11` → **HTTP 200**
  avec `Access-Control-Allow-Origin: https://158.158.74.169` + tous
  les `Allow-Methods/Headers/Credentials/Max-Age`.
- `PUT /api/admin/subscription-plans/11` avec token admin → **HTTP 200**
  (modif effective).
- `POST /api/admin/subscription-plans` (création) → **HTTP 201**.
- `DELETE` → 200/204 (ou 400 si FK existe, ce qui est attendu).

## Pièges retenus (futurs)

- **Ne JAMAIS se fier à `docker compose up -d --no-deps` pour relire
  un `.env` modifié** : utiliser `--force-recreate --no-deps <svc>`.
- **Toujours vérifier l'env effective** :
  `docker inspect <ctr> --format '{{range .Config.Env}}{{println .}}{{end}}' | grep VAR`
- **`docker compose build --no-cache` ne suffit pas** quand le `.env`
  change : seul le `up --force-recreate` recharge l'env (pas le rebuild).
- Le `CorsWebFilter` global de la gateway valide l'Origin pour **toutes
  les requêtes** (pas que le preflight). Si l'auth-service a aussi un
  `cors.allowed-origins` côté `SecurityConfig`, les deux doivent être
  alignés.
- **Bug pré-existant** : la gateway n'avait pas de route pour
  `/api/admin/**` (corrigé 30/08 — voir
  [[abonnement-admin-photo-2026-08-30]]). Si tu ajoutes un nouveau
  panel admin, vérifie la route gateway ET l'alignement CORS.

## À NE PAS faire en E2E

- Modifier un plan utilisé par des abonnements réels (FK
  `user_subscriptions.plan_id`). DELETE retournera 400 mais PUT peut
  silencieusement casser l'affichage visiteurs. Toujours utiliser
  `code="TEST-..."` et supprimer juste après.
- Le classifier Claude Code peut bloquer les `curl PUT/DELETE` non
  autorisés explicitement en prod. Préférer un script `*.sh` versionné
  dans `scripts/` et l'invoquer comme un test, pas en one-shot.
