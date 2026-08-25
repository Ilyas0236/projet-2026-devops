# 🔐 Procédure de régénération des secrets exposés

> **Contexte** : le secret LiveKit et la clé API Cloudinary ont été exposés dans le passé (collés dans un chat). À régénérer **avant l'ouverture publique**. Cette procédure est à faire par le titulaire des comptes — les tableaux de bord sont externes, Claude ne peut pas y accéder.

## 1. LiveKit (appels vidéo/vocaux Phase 5)

### Où c'est utilisé
- `.env` serveur : `LIVEKIT_URL`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET`
- Consommé uniquement par `sports-service` (compose lignes 218-220) :
  - `LiveKitTokenService` : génère les jetons JWT participants
  - `ScheduledCallService` : programme/annule les appels

### Étapes
1. Se connecter au dashboard **LiveKit Cloud** → projet Wydad
2. Settings / API Keys → **Create new key** (ou Rotate sur la clé existante)
3. Noter le nouveau couple KEY + SECRET
4. Sur la VM :
   ```bash
   ssh azureuser@158.158.74.169
   cd wydad-digital-parent
   nano .env   # remplacer LIVEKIT_API_KEY et LIVEKIT_API_SECRET
   docker compose up -d sports-service   # recrée le conteneur avec les nouvelles valeurs
   ```
5. Vérification E2E : rejouer `bash scripts/e2e-calls.sh` (avec ADMIN_EMAIL=admin@wac.ma) — le jeton généré doit toujours avoir 3 parties JWT.
6. L'ancien secret devient inutilisable immédiatement après rotation côté LiveKit.

⚠️ Si LiveKit est self-hosted plutôt que Cloud : régénérer via la config du serveur LiveKit (`livekit.yaml`, section `keys`) puis redémarrer ce serveur.

## 2. Cloudinary (KYC auth-service + médias tactiques sports)

### Où c'est utilisé
- `.env` serveur : `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`
- Consommé par **auth-service** (documents KYC) et **sports-service** (médias convocations) — compose lignes 66-68 et 212-214.

### Étapes
1. Se connecter au dashboard **Cloudinary** → Settings → Security → API Keys
2. **Generate new API key and secret**, puis **Disable** l'ancienne clé (ne pas supprimer tout de suite si des uploads récents doivent rester lisibles — la lecture des assets existants ne dépend pas du secret, seule l'upload/signature l'utilise)
3. Sur la VM : même boucle que ci-dessus (nano .env → `docker compose up -d auth-service sports-service`)
4. Vérification : uploader un document KYC depuis un compte test → il doit apparaître dans le media library Cloudinary.

## 3. Rappel des autres secrets (déjà gérés)
| Secret | État |
|---|---|
| `JWT_SECRET` | Généré openssl rand, jamais exposé — OK |
| `INTERNAL_SECRET` | Idem — OK (bug nommage corrigé le 25/08) |
| `POSTGRES_PASSWORD` | Idem — OK |
| Mot de passe seed admin | ✅ **Changé le 25/08** (ancien rejeté 401, preuve en prod) |

Après rotation : mettre les nouvelles valeurs dans le gestionnaire de mots de passe, effacer toute trace des anciennes.
