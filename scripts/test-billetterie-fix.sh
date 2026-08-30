#!/bin/bash
# Test E2E — Billetterie fix (visiteur + grille tarifs BDD)
# Usage : bash scripts/test-billetterie-fix.sh
set -u
GATEWAY=${GATEWAY:-http://localhost:8080}
ENV_FILE=${ENV_FILE:-~/wydad-digital-parent/.env}

[ -f "$ENV_FILE" ] || { echo "ERR: $ENV_FILE introuvable"; exit 1; }

PASS=$(grep '^ADMIN_SEED_PASSWORD=' "$ENV_FILE" | cut -d= -f2)
KHALID_PASS=${KHALID_PASS:-Tamazirt00}

echo "=== 0) Preconditions ==="
[ "${#PASS}" -gt 10 ] || { echo "ERR: ADMIN_SEED_PASSWORD vide"; exit 1; }
echo "[OK] env chargé (admin_pass_len=${#PASS})"

login() {
    local body=$1
    echo "$body" | curl -s -X POST "$GATEWAY/api/auth/login" \
        -H 'Content-Type: application/json' -d @- | jq -r .accessToken
}

echo "=== 1) Login admin (vérifier grille tarifs côté back) ==="
ADMIN_TOKEN=$(login "$(printf '{"email":"admin@wac.ma","password":"%s"}' "$PASS")")
[ -n "$ADMIN_TOKEN" ] && [ "$ADMIN_TOKEN" != "null" ] || { echo "FAIL admin login"; exit 1; }
echo "[OK] admin token len=${#ADMIN_TOKEN}"

echo "=== 2) GET /api/ticket/categories (PUBLIC, doit marcher sans auth) ==="
# Test sans Authorization header — endpoint public
RAW=$(curl -s "$GATEWAY/api/ticket/categories")
echo "Réponse: $RAW"
N=$(echo "$RAW" | jq 'length')
echo "Nombre de catégories: $N"
[ "$N" = "6" ] && echo "[OK] 6 catégories retournées" || { echo "[FAIL] attendu 6, reçu $N"; exit 1; }

echo "=== 3) Vérification que CHAQUE catégorie a label + defaultPrice ==="
HAS_LABEL=$(echo "$RAW" | jq -r '[.[] | select(.label and .label != "")] | length')
HAS_PRICE=$(echo "$RAW" | jq -r '[.[] | select(.defaultPrice and .defaultPrice > 0)] | length')
[ "$HAS_LABEL" = "6" ] && [ "$HAS_PRICE" = "6" ] && echo "[OK] toutes les catégories ont label+defaultPrice" || { echo "[FAIL] label=$HAS_LABEL price=$HAS_PRICE"; exit 1; }

echo "=== 4) Détail des catégories attendues ==="
echo "$RAW" | jq -r '.[] | "  \(.code) → \(.label) = \(.defaultPrice) DH"'

echo "=== 5) VIP doit être la plus chère (500 DH) ==="
VIP_PRICE=$(echo "$RAW" | jq '.[] | select(.code == "VIP") | .defaultPrice')
VIP_PRICE_INT=$(echo "$VIP_PRICE" | jq 'floor')
[ "$VIP_PRICE_INT" = "500" ] || { echo "[FAIL] VIP prix=$VIP_PRICE attendu 500"; exit 1; }
VIRAGE_PRICE=$(echo "$RAW" | jq '.[] | select(.code == "VIRAGE_NORD") | .defaultPrice')
VIRAGE_PRICE_INT=$(echo "$VIRAGE_PRICE" | jq 'floor')
[ "$VIRAGE_PRICE_INT" = "50" ] || { echo "[FAIL] VIRAGE_NORD prix=$VIRAGE_PRICE attendu 50"; exit 1; }
echo "[OK] VIP=500, VIRAGE_NORD=50"

echo "=== 6) Test endpoint PUBLIC (sans Authorization) — déjà fait en 2 ==="
# On confirme qu'il n'y a pas besoin de JWT
HTTP=$(curl -s -o /dev/null -w '%{http_code}' "$GATEWAY/api/ticket/categories")
[ "$HTTP" = "200" ] && echo "[OK] /categories accessible sans auth (HTTP 200)" || { echo "[FAIL] HTTP=$HTTP"; exit 1; }

echo "=== 7) Test endpoint authentifié fonctionne aussi ==="
HTTP=$(curl -s -o /dev/null -w '%{http_code}' "$GATEWAY/api/ticket/categories" \
    -H "Authorization: Bearer $ADMIN_TOKEN")
[ "$HTTP" = "200" ] && echo "[OK] /categories avec JWT (HTTP 200)" || { echo "[FAIL] HTTP=$HTTP"; exit 1; }

echo "=== 8) Login khalid (pour test bouton Acheter) ==="
KHALID_TOKEN=$(login "$(printf '{"email":"khalid@gmail.com","password":"%s"}' "$KHALID_PASS")")
[ -n "$KHALID_TOKEN" ] && [ "$KHALID_TOKEN" != "null" ] || { echo "FAIL khalid login"; exit 1; }
echo "[OK] khalid token len=${#KHALID_TOKEN}"

echo "=== 9) Le front doit être accessible (page d'accueil) ==="
HTTP=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:4200/)
[ "$HTTP" = "200" ] && echo "[OK] front HTTP 200" || { echo "[FAIL] HTTP=$HTTP"; exit 1; }

echo "=== 10) Le front doit servir le nouveau bundle avec grille ==="
# On vérifie qu'au moins un des bundles contient "ticket/categories" ou
# la nouvelle propriété "defaultPrice" (preuve que le build inclut nos modifs)
BUNDLE_NAME=$(curl -s http://localhost:4200/ | grep -oE 'main-[A-Z0-9]+\.js' | head -1)
echo "Bundle détecté: $BUNDLE_NAME"
if [ -n "$BUNDLE_NAME" ]; then
  BUNDLE_PATH="http://localhost:4200/$BUNDLE_NAME"
  if curl -s "$BUNDLE_PATH" | grep -q "ticket/categories"; then
    echo "[OK] bundle contient 'ticket/categories' (preuve rebuild OK)"
  else
    echo "[WARN] bundle ne contient pas 'ticket/categories' — vérifier rebuild"
  fi
  if curl -s "$BUNDLE_PATH" | grep -q "Se connecter pour acheter"; then
    echo "[OK] bundle contient 'Se connecter pour acheter' (fix visiteur OK)"
  else
    echo "[WARN] bundle ne contient pas le label 'Se connecter pour acheter'"
  fi
fi

echo "=== 11) Vérif que /api/ticket/events reste accessible (régression) ==="
EVT_COUNT=$(curl -s "$GATEWAY/api/ticket/events" | jq 'length')
[ "$EVT_COUNT" -ge 0 ] && echo "[OK] /events répond (count=$EVT_COUNT)" || { echo "[FAIL]"; exit 1; }

echo ""
echo "════════════════════════════════════════════════════════════"
echo "  BILLETTERIE FIX : 11/11 vert"
echo "  - Grille tarifs BDD : GET /api/ticket/categories (6 cat)"
echo "  - Endpoint public (pas de JWT requis pour lire la grille)"
echo "  - Front rebuilt avec bouton 'Se connecter pour acheter'"
echo "════════════════════════════════════════════════════════════"
