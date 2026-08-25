#!/usr/bin/env bash
# Phase 5 — E2E prod : programmer un appel (admin) -> agenda -> jeton -> annulation.
# Usage: ADMIN_EMAIL=... ADMIN_PASSWORD=... ./e2e-calls.sh [host]
#        (défaut http://localhost:8080, à lancer sur la VM)
# Les identifiants viennent de l'environnement ou du .env serveur — JAMAIS du dépôt.
set -euo pipefail
HOST="${1:-http://localhost:8080}"
ADMIN_EMAIL="${ADMIN_EMAIL:?ADMIN_EMAIL requis (source .env serveur, pas versionné)}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:?ADMIN_PASSWORD requis}"

login() {
  curl -s -X POST "$HOST/api/auth/login" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$1\",\"password\":\"$2\"}" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4
}

ADMIN_TOKEN=$(login "$ADMIN_EMAIL" "$ADMIN_PASSWORD")
echo "== media-status: $(curl -s $HOST/api/sports/calls/media-status -H "Authorization: Bearer $ADMIN_TOKEN")"

FUTURE=$(date -u -d '+2 hours' +%Y-%m-%dT%H:%M:%S)
CALL=$(curl -s -X POST "$HOST/api/sports/calls" -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"title\":\"E2E appel test\",\"scheduledAt\":\"$FUTURE\",\"durationMinutes\":15,\"target\":\"UTILISATEURS\",\"targetUserIds\":[1]}")
CALL_ID=$(echo "$CALL" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
echo "== appel cree: id=$CALL_ID room=$(echo "$CALL" | grep -o '"roomName":"[^"]*"' | cut -d'"' -f4)"

echo "== agenda admin: $(curl -s $HOST/api/sports/calls/mine -H "Authorization: Bearer $ADMIN_TOKEN" | head -c 200)"

TOKEN=$(curl -s -X POST "$HOST/api/sports/calls/$CALL_ID/token" -H "Authorization: Bearer $ADMIN_TOKEN")
echo "== jeton: url=$(echo "$TOKEN" | grep -o '"url":"[^"]*"' | cut -d'"' -f4) jwt_parts=$(echo "$TOKEN" | grep -o '"token":"[^"]*"' | cut -d'"' -f4 | awk -F. '{print NF}')"

CANCELED=$(curl -s -X POST "$HOST/api/sports/calls/$CALL_ID/cancel" -H "Authorization: Bearer $ADMIN_TOKEN" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
echo "== statut apres annulation: $CANCELED"
AFTER=$(curl -s -X POST "$HOST/api/sports/calls/$CALL_ID/token" -H "Authorization: Bearer $ADMIN_TOKEN" -o /dev/null -w '%{http_code}')
echo "== jeton apres annulation (attendu 409/400/500): $AFTER"
echo "== nettoyage: DELETE non implemente (statut ANNULE suffit)"
