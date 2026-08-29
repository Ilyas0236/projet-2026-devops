#!/usr/bin/env bash
# ============================================================================
# test-email-mock.sh — vérifie que l'orchestrateur de notification trace bien
# l'envoi MOCK et que la notification passe en status SENT.
#
# Pré-requis : notification-service lancé, auth-service lancé.
# Test : crée un user de test (ou réutilise un existant), POST /api/notification
#        /send avec type=EMAIL, vérifie status=SENT et présence du log MOCK.
# ============================================================================
set -euo pipefail

BASE_GATEWAY="${BASE_GATEWAY:-http://localhost:8080}"
NOTIF_DIRECT="${NOTIF_DIRECT:-http://localhost:8086}"
TEST_EMAIL="${TEST_EMAIL:-test.email.mock@wydad.ma}"

green() { printf "\033[32m%s\033[0m\n" "$*"; }
red()   { printf "\033[31m%s\033[0m\n" "$*"; }
blue()  { printf "\033[34m%s\033[0m\n" "$*"; }

blue "── Test EmailService MOCK ──"
blue "Test email : $TEST_EMAIL"
blue "Gateway    : $BASE_GATEWAY"
blue "Notif dir  : $NOTIF_DIRECT"

# 1) Health check notification-service
blue "→ 1) Health check notification-service"
if ! curl -s -f "$NOTIF_DIRECT/actuator/health" > /dev/null; then
  red "✖ notification-service inaccessible à $NOTIF_DIRECT"
  exit 1
fi
green "✓ notification-service healthy"

# 2) Récupération d'un user de test (premier user de la base, ou on en crée un
#    via auth-service /register). On suppose qu'un user admin@wac.ma existe
#    et on l'utilise pour s'authentifier. Si non, on demande à l'appelant de
#    passer ADMIN_TOKEN dans l'environnement.
if [[ -z "${ADMIN_TOKEN:-}" ]]; then
  blue "→ 2) Login admin (admin@wac.ma) pour récupérer un token"
  ADMIN_TOKEN=$(curl -s -X POST "$BASE_GATEWAY/api/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"email":"admin@wac.ma","password":"Admin2026!"}' \
    | sed -nE 's/.*"accessToken":"([^"]+)".*/\1/p')
  if [[ -z "$ADMIN_TOKEN" ]]; then
    red "✖ Login admin échoué — vérifier identifiants (ou exporter ADMIN_TOKEN)"
    exit 1
  fi
fi
green "✓ Admin token récupéré"

# 3) Création d'une notification EMAIL pour le user de test
blue "→ 3) POST /api/notification/send type=EMAIL"
RESP=$(curl -s -X POST "$BASE_GATEWAY/api/notification/send" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{
    \"userId\": null,
    \"userEmail\": \"$TEST_EMAIL\",
    \"type\": \"EMAIL\",
    \"title\": \"[TEST MOCK] Bienvenue\",
    \"message\": \"Ceci est un email de test pour vérifier le mode MOCK.\",
    \"targetUrl\": null
  }")
echo "$RESP" | head -c 200
echo

# 4) Vérification status=SENT
STATUS=$(echo "$RESP" | sed -nE 's/.*"status":"([^"]+)".*/\1/p')
if [[ "$STATUS" == "SENT" ]]; then
  green "✓ Notification créée avec status=SENT"
else
  red "✖ status attendu SENT, reçu : '$STATUS'"
  exit 1
fi

# 5) Vérification log MOCK
blue "→ 4) Vérification du log MOCK dans notification-service"
if docker ps --format '{{.Names}}' | grep -q notification-service; then
  if docker logs notification-service --tail 100 2>&1 | grep -q "MOCK SENDGRID"; then
    green "✓ Log MOCK présent"
  else
    red "✖ Log MOCK introuvable dans les 100 dernières lignes"
    exit 1
  fi
else
  blue "(conteneur notification-service introuvable — vérification log ignorée)"
fi

green ""
green "── Test EmailService MOCK : OK ──"
