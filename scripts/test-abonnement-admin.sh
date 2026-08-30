#!/bin/bash
# Test E2E — Refonte page /abonnement : photo de carte pilotée par l'admin
#
# Scénario : on s'assure que l'admin peut uploader une photo de carte
# (Cloudinary), que le catalogue public la renvoie, puis qu'on peut
# la retirer. Vérifie aussi que l'ancien fallback "✓ 15 matchs..." a
# disparu côté front (la home et /abonnement n'incluent plus de
# privileges statiques dans le HTML servi au navigateur).
#
# Pré-requis :
#   - auth-service démarré (et la colonne `card_image_url` doit exister
#     dans subscription_plans — ddl-auto=update l'ajoute tout seul
#     après le premier restart qui suit la montée de version)
#   - admin@wac.ma existe et a role ADMIN
#   - Cloudinary configuré (sinon mode dégradé → URL `local:plan:...`)

set +e

BASE="${WAC_BASE:-http://localhost:8080}"
ADMIN_EMAIL="admin@wac.ma"
# Lu dans /home/azureuser/wydad-digital-parent/.env (ADMIN_SEED_PASSWORD)
# Le mot de passe historique "gW2Ik9f6unGIuU1y7Y5Zy70A82" a été changé
# par l'admin (cf. DEPLOIEMENT-AZURE.md §6 — 25/08/2026).
# Lu par défaut dans /home/azureuser/wydad-digital-parent/.env
# (clé ADMIN_SEED_PASSWORD). Le mot de passe historique
# "gW2Ik9f6unGIuU1y7Y5Zy70A82" a été changé par l'admin (cf.
# DEPLOIEMENT-AZURE.md §6 — 25/08/2026). On autorise l'override
# via la variable d'environnement ADMIN_PASS (utile en CI).
ADMIN_PASS="${ADMIN_PASS:-}"
if [ -z "$ADMIN_PASS" ] && [ -f /home/azureuser/wydad-digital-parent/.env ]; then
  ADMIN_PASS=$(grep -E '^ADMIN_SEED_PASSWORD=' /home/azureuser/wydad-digital-parent/.env | cut -d= -f2-)
fi
if [ -z "$ADMIN_PASS" ]; then
  echo "ERREUR: ADMIN_PASS non défini. Renseignez la variable d'environnement" >&2
  echo "ADMIN_PASS ou exécutez ce script sur la VM où .env est lisible." >&2
  exit 1
fi

PASS=0
FAIL=0
declare -a BUGS

ok()   { echo "  [OK  ] $1"; PASS=$((PASS+1)); }
ko()   { echo "  [FAIL] $1 : $2"; BUGS+=("$1 : $2"); FAIL=$((FAIL+1)); }

# Génère un PNG 1x1 transparent (en base64) — assez léger pour
# passer la validation 5 Mo côté serveur.
PNG_B64="iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNgAAIAAAUAAen63NgAAAAASUVORK5CYII="
PNG_FILE="/tmp/test-card-$(date +%s).png"
echo "$PNG_B64" | base64 -d > "$PNG_FILE"
ls -la "$PNG_FILE"

# 1. Login admin
echo ""
echo "=== 1. Login admin ==="
A_LOGIN=$(curl -s -X POST "$BASE/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASS\"}")
A_TOK=$(echo "$A_LOGIN" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("accessToken",""))')
A_ID=$(echo "$A_LOGIN" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("id",""))')
if [ -z "$A_TOK" ]; then
  ko "Login admin" "$A_LOGIN"
  exit 1
fi
ok "Login admin (id=$A_ID)"

# 2. Catalogue public (sans JWT) — on veut au moins 1 plan
echo ""
echo "=== 2. Catalogue public /api/auth/subscriptions/plans ==="
PLANS=$(curl -s -o /tmp/plans.json -w '%{http_code}' "$BASE/api/auth/subscriptions/plans")
if [ "$PLANS" != "200" ]; then
  ko "GET plans public" "HTTP=$PLANS body=$(cat /tmp/plans.json)"
  exit 1
fi
N_PLANS=$(python3 -c 'import json; print(len(json.load(open("/tmp/plans.json"))))' 2>/dev/null)
if [ "$N_PLANS" -lt 1 ]; then
  ko "GET plans public" "0 plan retourné"
  exit 1
fi
ok "GET plans public ($N_PLANS plans)"

# 3. Récupère l'ID d'un plan (le 1er) pour l'upload
PLAN_ID=$(python3 -c 'import json; print(json.load(open("/tmp/plans.json"))[0]["id"])')
PLAN_CODE=$(python3 -c 'import json; print(json.load(open("/tmp/plans.json"))[0]["code"])')
ok "Cible : plan id=$PLAN_ID code=$PLAN_CODE"

# 4. Upload photo (multipart) en tant qu'admin
# Préfixe = /api/admin/subscription-plans (cf. AdminSubscriptionPlanController
# + route gateway dédiée). NE PAS utiliser /api/auth/admin/... : c'est
# uniquement pour l'authentification.
echo ""
echo "=== 3. POST /api/admin/subscription-plans/$PLAN_ID/card-image ==="
UPLOAD=$(curl -s -o /tmp/upload.json -w '%{http_code}' \
  -X POST -H "Authorization: Bearer $A_TOK" \
  -F "file=@$PNG_FILE;type=image/png" \
  "$BASE/api/admin/subscription-plans/$PLAN_ID/card-image")
echo "HTTP=$UPLOAD body=$(cat /tmp/upload.json)"
if [ "$UPLOAD" != "200" ]; then
  ko "Upload photo carte" "HTTP=$UPLOAD"
  exit 1
fi
URL=$(python3 -c 'import json; print(json.load(open("/tmp/upload.json")).get("cardImageUrl",""))' 2>/dev/null)
if [ -z "$URL" ]; then
  ko "Upload photo carte" "cardImageUrl absent du JSON"
  exit 1
fi
ok "Upload photo carte → $URL"

# 5. Re-GET public → l'URL doit apparaître
echo ""
echo "=== 4. Catalogue public reflète la photo ==="
curl -s -o /tmp/plans2.json -w '' "$BASE/api/auth/subscriptions/plans"
URL2=$(python3 -c "
import json
plans = json.load(open('/tmp/plans2.json'))
for p in plans:
    if p['id'] == $PLAN_ID:
        print(p.get('cardImageUrl','') or '')
        break
")
if [ -z "$URL2" ]; then
  ko "Photo visible côté public" "cardImageUrl vide sur plan $PLAN_ID"
else
  ok "Photo visible côté public → $URL2"
fi

# 6. Suppression de la photo
echo ""
echo "=== 5. DELETE /api/admin/subscription-plans/$PLAN_ID/card-image ==="
DELETE=$(curl -s -o /tmp/delete.json -w '%{http_code}' \
  -X DELETE -H "Authorization: Bearer $A_TOK" \
  "$BASE/api/admin/subscription-plans/$PLAN_ID/card-image")
echo "HTTP=$DELETE body=$(cat /tmp/delete.json)"
if [ "$DELETE" != "200" ]; then
  ko "Suppression photo" "HTTP=$DELETE"
else
  ok "Suppression photo"
fi

# 7. Re-GET → la photo doit être partie
echo ""
echo "=== 6. Photo bien retirée du catalogue public ==="
curl -s -o /tmp/plans3.json -w '' "$BASE/api/auth/subscriptions/plans"
URL3=$(python3 -c "
import json
plans = json.load(open('/tmp/plans3.json'))
for p in plans:
    if p['id'] == $PLAN_ID:
        print(p.get('cardImageUrl','') or '<VIDE>')
        break
")
echo "URL3=$URL3"
if [ "$URL3" = "<VIDE>" ] || [ -z "$URL3" ]; then
  ok "Photo retirée du catalogue public"
else
  ko "Photo encore présente après DELETE" "URL3=$URL3"
fi

# 8. Garde-fou : le bundle front ne contient plus les fallbacks statiques
#    (vérif grossière sur le dist servi, ou sur les sources front si pas
#    de dist). Les fallbacks "15 matchs à domicile" / "QR code personnel"
#    ne doivent plus apparaître dans les sources.
echo ""
echo "=== 7. Aucun fallback statique dans les sources front ==="
FRONT_DIR="$(dirname "$0")/../wydad-frontend/src/app/pages"
if [ -d "$FRONT_DIR" ]; then
  # On vérifie qu'aucune CARTE visiteur (pages abonnement, home, ou
  # composant carte) ne contient encore un fallback codé en dur.
  # Les chaînes "15 matchs à domicile" et "QR code personnel"
  # peuvent apparaître dans le placeholder du modal admin (aide à la
  # saisie) — c'est légitime, on l'autorise. Le test vise
  # strictement les fichiers qui rendent les cartes visiteurs.
  VISITOR_PAGES=(
    "$FRONT_DIR/abonnement"
    "$FRONT_DIR/home"
  )
  for needle in "15 matchs à domicile" "Carte PDF instantanée"; do
    found=0
    for dir in "${VISITOR_PAGES[@]}"; do
      if [ -d "$dir" ] && grep -rq "$needle" "$dir" 2>/dev/null; then
        ko "Fallback '$needle'" "présent dans $dir"
        found=1
      fi
    done
    if [ "$found" = "0" ]; then
      ok "Pas de fallback statique '$needle' sur les cartes visiteurs"
    fi
  done
else
  echo "  [SKIP] $FRONT_DIR introuvable (test à faire sur la VM après pull)"
fi

# Cleanup
rm -f "$PNG_FILE"

echo ""
echo "============================================="
echo "  RÉSUMÉ: $PASS OK, $FAIL FAIL"
echo "============================================="
if [ $FAIL -gt 0 ]; then
  echo "Bugs / points à vérifier :"
  for b in "${BUGS[@]}"; do echo "  - $b"; done
  exit 1
fi
echo "  TOUS LES TESTS B.12 PHOTO DE CARTE SONT PASSES"
exit 0
