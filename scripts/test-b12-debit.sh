#!/bin/bash
# Test E2E B.12 — achat débite E-Cash + restriction supporters
# Usage : bash scripts/test-b12-debit.sh
set -u
GATEWAY=${GATEWAY:-http://localhost:8080}
ENV_FILE=${ENV_FILE:-~/wydad-digital-parent/.env}

[ -f "$ENV_FILE" ] || { echo "ERR: $ENV_FILE introuvable"; exit 1; }

PASS=$(grep '^ADMIN_SEED_PASSWORD=' "$ENV_FILE" | cut -d= -f2)
SECRET=$(grep '^INTERNAL_SECRET=' "$ENV_FILE" | cut -d= -f2)
KHALID_PASS=${KHALID_PASS:-Tamazirt00}

PASS_LEN=${#PASS}
SECRET_LEN=${#SECRET}
KHALID_PASS_LEN=${#KHALID_PASS}

login() {
    local body=$1
    echo "$body" | curl -s -X POST "$GATEWAY/api/auth/login" \
        -H 'Content-Type: application/json' -d @- | jq -r .accessToken
}

echo "=== 0) Check preconditions (longueurs OK, mot de passe non vide) ==="
[ "$PASS_LEN" -gt 10 ] || { echo "ERR: ADMIN_SEED_PASSWORD vide"; exit 1; }
[ "$SECRET_LEN" -gt 0 ] || { echo "ERR: INTERNAL_SECRET vide"; exit 1; }
echo "[OK] env charge (admin_pass_len=$PASS_LEN, secret_len=$SECRET_LEN)"

echo "=== 1) Login admin ==="
ADMIN_TOKEN=$(login "$(printf '{"email":"admin@wac.ma","password":"%s"}' "$PASS")")
[ -n "$ADMIN_TOKEN" ] && [ "$ADMIN_TOKEN" != "null" ] || { echo "FAIL admin login"; exit 1; }
echo "[OK] admin token len=${#ADMIN_TOKEN}"

echo "=== 2) Login khalid ==="
KHALID_TOKEN=$(login "$(printf '{"email":"khalid@gmail.com","password":"%s"}' "$KHALID_PASS")")
[ -n "$KHALID_TOKEN" ] && [ "$KHALID_TOKEN" != "null" ] || { echo "FAIL khalid login (mdp par defaut: $KHALID_PASS_LEN chars)"; exit 1; }
echo "[OK] khalid token len=${#KHALID_TOKEN}"

echo "=== 3) Admin tente achat (attendu 403) ==="
RES=$(curl -s -o /tmp/r.json -w '%{http_code}' -X POST "$GATEWAY/api/auth/subscriptions/purchase" \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"planCode":"PEL-6","cardNumber":"4242424242424242","expiryDate":"12/29","cvv":"123","otp":"000000"}')
echo "HTTP=$RES body=$(cat /tmp/r.json)"
[ "$RES" = "403" ] && echo "[OK] admin bloque par @PreAuthorize" || { echo "[FAIL] attendu 403, recu $RES"; exit 1; }

echo "=== 4) Plans ==="
curl -s "$GATEWAY/api/auth/subscriptions/plans" | jq -r '.[] | "\(.code) reg=\(.regularPrice) adh=\(.adherentPrice)"'

echo "=== 5) Forcer solde khalid=0 (UPDATE direct, sans toucher transactions) ==="
docker exec wydad-postgres psql -U wydad -d payment_db -c "UPDATE e_cash_accounts SET balance=0, updated_at=NOW() WHERE email='khalid@gmail.com';" >/dev/null
BAL=$(docker exec wydad-postgres psql -U wydad -d payment_db -t -A -c "SELECT balance FROM e_cash_accounts WHERE email='khalid@gmail.com';")
echo "solde_force=$BAL"
[ "$BAL" = "0" ] || [ "$BAL" = "0.00" ] && echo "[OK] solde a 0" || { echo "ERR: solde=$BAL pas 0"; exit 1; }

echo "=== 6) khalid solde=0 tente achat (attendu 402) ==="
RES=$(curl -s -o /tmp/r.json -w '%{http_code}' -X POST "$GATEWAY/api/auth/subscriptions/purchase" \
  -H "Authorization: Bearer $KHALID_TOKEN" -H 'Content-Type: application/json' \
  -d '{"planCode":"PEL-6","cardNumber":"4242424242424242","expiryDate":"12/29","cvv":"123","otp":"000000"}')
echo "HTTP=$RES body=$(cat /tmp/r.json)"
[ "$RES" = "402" ] && echo "[OK] solde insuffisant detecte par payment-service" || { echo "[FAIL] attendu 402, recu $RES"; exit 1; }

echo "=== 7) Recharge E-Cash 500 via /api/payment/card (carte simulee) ==="
# /api/payment/card attend : cardNumber, expiryDate, cvv, otp, amount
# email optionnel (derive du JWT par defaut)
REF="TEST-CARD-$(date +%s)"
TOPUP_BODY='{"cardNumber":"4242424242424242","expiryDate":"12/29","cvv":"123","otp":"000000","amount":500}'
TOPUP=$(echo "$TOPUP_BODY" | curl -s -X POST "$GATEWAY/api/payment/card" \
  -H "Authorization: Bearer $KHALID_TOKEN" -H 'Content-Type: application/json' -d @-)
echo "card=$TOPUP"
echo "$TOPUP" | jq -e .reference >/dev/null 2>&1 || { echo "FAIL: card recharge rejetee"; exit 1; }
echo "[OK] card OK"

echo "=== 8) khalid achete PEL-6 (attendu 201, transaction B12-*) ==="
RES=$(curl -s -o /tmp/r.json -w '%{http_code}' -X POST "$GATEWAY/api/auth/subscriptions/purchase" \
  -H "Authorization: Bearer $KHALID_TOKEN" -H 'Content-Type: application/json' \
  -d '{"planCode":"PEL-6","cardNumber":"4242424242424242","expiryDate":"12/29","cvv":"123","otp":"000000"}')
echo "HTTP=$RES body=$(cat /tmp/r.json | head -c 600)"
[ "$RES" = "201" ] && echo "[OK] achat reussi" || { echo "[FAIL] attendu 201, recu $RES"; exit 1; }

echo "=== 9) Verif transactions (derniere doit etre DEBIT avec ref B12-*) ==="
docker exec wydad-postgres psql -U wydad -d payment_db -c "SELECT id, email, type, amount, reference FROM transactions WHERE email='khalid@gmail.com' ORDER BY id DESC LIMIT 5;"
LAST_REF=$(docker exec wydad-postgres psql -U wydad -d payment_db -t -A -c "SELECT reference FROM transactions WHERE email='khalid@gmail.com' ORDER BY id DESC LIMIT 1;")
LAST_TYPE=$(docker exec wydad-postgres psql -U wydad -d payment_db -t -A -c "SELECT type FROM transactions WHERE email='khalid@gmail.com' ORDER BY id DESC LIMIT 1;")
LAST_AMT=$(docker exec wydad-postgres psql -U wydad -d payment_db -t -A -c "SELECT amount FROM transactions WHERE email='khalid@gmail.com' ORDER BY id DESC LIMIT 1;")
[ "$LAST_TYPE" = "DEBIT" ] || { echo "[FAIL] derniere transaction type=$LAST_TYPE (attendu DEBIT)"; exit 1; }
case "$LAST_REF" in B12-*) echo "[OK] type=DEBIT ref=B12-* amt=$LAST_AMT";; *) echo "[FAIL] ref inattendue: $LAST_REF"; exit 1;; esac

echo "=== 10) Verif subscription creee ==="
docker exec wydad-postgres psql -U wydad -d auth_db -c "SELECT id, plan_id, status, paid_amount, transaction_ref FROM user_subscriptions WHERE user_id=(SELECT id FROM users WHERE email='khalid@gmail.com') ORDER BY id DESC LIMIT 3;"

echo "=== 11) Solde final khalid ==="
docker exec wydad-postgres psql -U wydad -d payment_db -c "SELECT email, type, SUM(amount) AS total FROM transactions WHERE email='khalid@gmail.com' GROUP BY email, type ORDER BY email, type;"

echo ""
echo "════════════════════════════════════════════════════════════"
echo "  TOUS LES TESTS B.12 (debit E-Cash + restriction) : VERT"
echo "════════════════════════════════════════════════════════════"
