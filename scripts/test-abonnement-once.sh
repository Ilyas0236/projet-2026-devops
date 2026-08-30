#!/bin/bash
# Test E2E — Règle « un seul abonnement par saison » (30/08/2026)
# Vérifie qu'un utilisateur ne peut acheter qu'UNE carte d'abonnement
# par saison, et que toute tentative d'achat supplémentaire (upgrade
# ou ré-achat, même plan) renvoie 409 ALREADY_SUBSCRIBED.
#
# Scénario :
#   1. Login user NEUF (sans abonnement) → /me/active = 204
#   2. Premier achat (PEL-ONCE, 100 MAD) → 201 ACTIVE
#   3. /me/active = PEL-ONCE ACTIVE
#   4. Deuxième achat MÊME plan (PEL-ONCE) → 409 ALREADY_SUBSCRIBED
#   5. Troisième achat AUTRE plan (PEL-ONCE-2, plus cher) → 409 ALREADY_SUBSCRIBED
#   6. /me/active = toujours PEL-ONCE (pas remplacé)
#   7. Cleanup admin
#
# Pré-requis : auth-service expose /api/auth/subscriptions/{purchase,me/active}
# et /api/admin/subscription-plans. Paiement SIMULÉ (carte 4242 + OTP 000000).

set +e

BASE="${WAC_BASE:-http://localhost:8080}"
ADMIN_EMAIL="admin@wac.ma"
: "${ADMIN_PASSWORD:?ADMIN_PASSWORD doit etre defini (source .env du serveur)}"
ADMIN_PASS="$ADMIN_PASSWORD"
USER_PASSWORD="TestPass123!"

PASS=0
FAIL=0
ok()   { echo "  ✅ $1"; PASS=$((PASS+1)); }
ko()   { echo "  ❌ $1"; FAIL=$((FAIL+1)); }

# ─── 0. Login admin ─────────────────────────────────────────────────
echo "=== 0. Login admin (cleanup + creation plans) ==="
A_LOGIN=$(curl -s -X POST $BASE/api/auth/login -H 'Content-Type: application/json' \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASS\"}")
A_TOK=$(echo "$A_LOGIN" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("accessToken",""))' 2>/dev/null)
if [ -z "$A_TOK" ]; then
  echo "ÉCHEC: login admin KO — $A_LOGIN"
  exit 1
fi
echo "admin OK"

# ─── 1. Création user NEUF ──────────────────────────────────────────
TS=$(date +%s)
USER_EMAIL="once-test-$TS@wac.ma"
USER_PHONE="+2126${TS:2:8}"
echo ""
echo "=== 1. Création user $USER_EMAIL (SANS abonnement) ==="
REG=$(curl -s -X POST $BASE/api/auth/register -H 'Content-Type: application/json' \
  -d "{\"email\":\"$USER_EMAIL\",\"password\":\"$USER_PASSWORD\",\"firstName\":\"Once\",\"lastName\":\"Test\",\"phone\":\"$USER_PHONE\"}")
U_ID=$(echo "$REG" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("id",""))' 2>/dev/null)
if [ -z "$U_ID" ]; then
  echo "ÉCHEC register : $REG"; exit 1
fi
echo "user id=$U_ID"

# Login user
U_LOGIN=$(curl -s -X POST $BASE/api/auth/login -H 'Content-Type: application/json' \
  -d "{\"email\":\"$USER_EMAIL\",\"password\":\"$USER_PASSWORD\"}")
U_TOK=$(echo "$U_LOGIN" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("accessToken",""))' 2>/dev/null)
U_ROLE=$(echo "$U_LOGIN" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("role","") or " ".join(json.load(sys.stdin).get("roles",[])))' 2>/dev/null)
echo "user role=$U_ROLE"

# Validation admin si EN_ATTENTE
if echo "$U_ROLE" | grep -qi "EN_ATTENTE\|ATTENTE"; then
  echo "  → compte EN_ATTENTE, validation admin"
  ME_HTTP=$(curl -s -o /tmp/me.json -w '%{http_code}' \
    -H "Authorization: Bearer $U_TOK" "$BASE/api/auth/me")
  U_NUMERIC_ID=$(python3 -c 'import json; print(json.load(open("/tmp/me.json")).get("id",""))' 2>/dev/null)
  if [ -n "$U_NUMERIC_ID" ]; then
    curl -s -X PATCH "$BASE/api/auth/admin/accounts/$U_NUMERIC_ID/validate" \
      -H "Authorization: Bearer $A_TOK" -H "X-User-Id: 1" -H "X-User-Email: $ADMIN_EMAIL" -H "X-User-Role: ADMIN" \
      >/dev/null 2>&1
    U_LOGIN=$(curl -s -X POST $BASE/api/auth/login -H 'Content-Type: application/json' \
      -d "{\"email\":\"$USER_EMAIL\",\"password\":\"$USER_PASSWORD\"}")
    U_TOK=$(echo "$U_LOGIN" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("accessToken",""))' 2>/dev/null)
    U_ROLE=$(echo "$U_LOGIN" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("role","") or " ".join(json.load(sys.stdin).get("roles",[])))' 2>/dev/null)
    echo "  → user validé, role=$U_ROLE"
  fi
fi

# /me/active doit etre vide (204 ou 404)
ME0_HTTP=$(curl -s -o /tmp/me0.json -w '%{http_code}' \
  -H "Authorization: Bearer $U_TOK" "$BASE/api/auth/subscriptions/me/active")
echo "/me/active initial HTTP=$ME0_HTTP"
if [ "$ME0_HTTP" = "204" ] || [ "$ME0_HTTP" = "404" ]; then
  ok "/me/active vide au depart (HTTP=$ME0_HTTP)"
else
  ko "/me/active devrait etre vide (HTTP=$ME0_HTTP)"
fi

# ─── 1b. Cleanup préventif des plans PEL-ONCE(-2) d'un run precedent ─
echo ""
echo "=== 1b. Cleanup préventif des plans PEL-ONCE(-2) éventuels ==="
docker exec wydad-postgres psql -U wydad -d auth_db -c \
  "DELETE FROM user_subscriptions WHERE plan_id IN (SELECT id FROM subscription_plans WHERE code IN ('PEL-ONCE','PEL-ONCE-2')); DELETE FROM subscription_plans WHERE code IN ('PEL-ONCE','PEL-ONCE-2');" \
  >/dev/null 2>&1
echo "  (cleanup OK ou aucun plan à supprimer)"

# ─── 2. Admin crée PEL-ONCE (100/80) et PEL-ONCE-2 (200/160) ─────────
echo ""
echo "=== 2. Admin crée PEL-ONCE (100/80) et PEL-ONCE-2 (200/160) ==="
P1=$(curl -s -X POST "$BASE/api/admin/subscription-plans" \
  -H "Authorization: Bearer $A_TOK" -H "X-User-Id: 1" -H "X-User-Email: $ADMIN_EMAIL" -H "X-User-Role: ADMIN" \
  -H 'Content-Type: application/json' \
  -d '{"code":"PEL-ONCE","name":"Plan Pelouse Once","regularPrice":100,"adherentPrice":80,"isActive":true,"displayOrder":97,"exceptionalPriority":false,"season":"2026-2027"}')
P1_ID=$(echo "$P1" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("id",""))' 2>/dev/null)
echo "PEL-ONCE id=$P1_ID"

P2=$(curl -s -X POST "$BASE/api/admin/subscription-plans" \
  -H "Authorization: Bearer $A_TOK" -H "X-User-Id: 1" -H "X-User-Email: $ADMIN_EMAIL" -H "X-User-Role: ADMIN" \
  -H 'Content-Type: application/json' \
  -d '{"code":"PEL-ONCE-2","name":"Plan Pelouse Once 2","regularPrice":200,"adherentPrice":160,"isActive":true,"displayOrder":96,"exceptionalPriority":false,"season":"2026-2027"}')
P2_ID=$(echo "$P2" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("id",""))' 2>/dev/null)
echo "PEL-ONCE-2 id=$P2_ID"

if [ -n "$P1_ID" ] && [ -n "$P2_ID" ]; then
  ok "creation des 2 plans par admin"
else
  ko "creation plans KO (P1=$P1 P2=$P2)"
  echo "On continue pour debug mais les tests d'achat vont echouer"
fi

# ─── 3. Premier achat PEL-ONCE → 201 ACTIVE ─────────────────────────
echo ""
echo "=== 3. Premier achat PEL-ONCE (100 MAD) → doit etre 201 ACTIVE ==="
PUR1_HTTP=$(curl -s -o /tmp/p1.json -w '%{http_code}' -X POST $BASE/api/auth/subscriptions/purchase \
  -H "Authorization: Bearer $U_TOK" -H "X-User-Id: $U_ID" -H "X-User-Email: $USER_EMAIL" \
  -H 'Content-Type: application/json' \
  -d '{"planCode":"PEL-ONCE","cardNumber":"4242424242424242","expiryDate":"12/29","cvv":"123","otp":"000000"}')
echo "HTTP=$PUR1_HTTP body=$(cat /tmp/p1.json)"
STATUS=$(python3 -c 'import json; print(json.load(open("/tmp/p1.json")).get("status",""))' 2>/dev/null)
PLAN=$(python3 -c 'import json; print(json.load(open("/tmp/p1.json")).get("planCode",""))' 2>/dev/null)
PAID=$(python3 -c 'import json; print(json.load(open("/tmp/p1.json")).get("paidAmount",0))' 2>/dev/null)
if [ "$PUR1_HTTP" = "201" ] && [ "$STATUS" = "ACTIVE" ] && [ "$PLAN" = "PEL-ONCE" ] && [ "$PAID" = "100.0" ]; then
  ok "1er achat OK (201 ACTIVE plan=PEL-ONCE paid=100)"
else
  ko "1er achat KO (HTTP=$PUR1_HTTP status=$STATUS plan=$PLAN paid=$PAID)"
fi

# ─── 4. /me/active = PEL-ONCE ACTIVE ────────────────────────────────
ME1_HTTP=$(curl -s -o /tmp/me1.json -w '%{http_code}' \
  -H "Authorization: Bearer $U_TOK" "$BASE/api/auth/subscriptions/me/active")
ME1_PLAN=$(python3 -c 'import json; print(json.load(open("/tmp/me1.json")).get("planCode",""))' 2>/dev/null)
ME1_STATUS=$(python3 -c 'import json; print(json.load(open("/tmp/me1.json")).get("status",""))' 2>/dev/null)
echo "/me/active HTTP=$ME1_HTTP plan=$ME1_PLAN status=$ME1_STATUS"
if [ "$ME1_HTTP" = "200" ] && [ "$ME1_PLAN" = "PEL-ONCE" ] && [ "$ME1_STATUS" = "ACTIVE" ]; then
  ok "/me/active = PEL-ONCE ACTIVE"
else
  ko "/me/active devrait etre PEL-ONCE ACTIVE"
fi

# ─── 5. Deuxième achat MÊME plan (PEL-ONCE) → 409 ALREADY_SUBSCRIBED ─
echo ""
echo "=== 5. 2e achat MEME plan (PEL-ONCE) → doit etre 409 ALREADY_SUBSCRIBED ==="
PUR2_HTTP=$(curl -s -o /tmp/p2.json -w '%{http_code}' -X POST $BASE/api/auth/subscriptions/purchase \
  -H "Authorization: Bearer $U_TOK" -H "X-User-Id: $U_ID" -H "X-User-Email: $USER_EMAIL" \
  -H 'Content-Type: application/json' \
  -d '{"planCode":"PEL-ONCE","cardNumber":"4242424242424242","expiryDate":"12/29","cvv":"123","otp":"000000"}')
echo "HTTP=$PUR2_HTTP body=$(cat /tmp/p2.json)"
ERR_CODE=$(python3 -c 'import json; print(json.load(open("/tmp/p2.json")).get("code",""))' 2>/dev/null)
ERR_MSG=$(python3 -c 'import json; print(json.load(open("/tmp/p2.json")).get("message",""))' 2>/dev/null)
if [ "$PUR2_HTTP" = "409" ] && [ "$ERR_CODE" = "ALREADY_SUBSCRIBED" ]; then
  ok "2e achat MEME plan refuse avec 409 ALREADY_SUBSCRIBED"
  if echo "$ERR_MSG" | grep -q "déjà un abonnement" && echo "$ERR_MSG" | grep -q "saison"; then
    ok "message 409 explicite (mentionne saison + regle)"
  else
    ko "message 409 peu clair: $ERR_MSG"
  fi
else
  ko "2e achat MÊME plan aurait du etre 409 (HTTP=$PUR2_HTTP code=$ERR_CODE)"
fi

# ─── 6. Tentative d'UPGRADE (PEL-ONCE → PEL-ONCE-2) → 409 ───────────
echo ""
echo "=== 6. Tentative d'UPGRADE (PEL-ONCE → PEL-ONCE-2) → doit etre 409 ==="
PUR3_HTTP=$(curl -s -o /tmp/p3.json -w '%{http_code}' -X POST $BASE/api/auth/subscriptions/purchase \
  -H "Authorization: Bearer $U_TOK" -H "X-User-Id: $U_ID" -H "X-User-Email: $USER_EMAIL" \
  -H 'Content-Type: application/json' \
  -d '{"planCode":"PEL-ONCE-2","cardNumber":"4242424242424242","expiryDate":"12/29","cvv":"123","otp":"000000"}')
echo "HTTP=$PUR3_HTTP body=$(cat /tmp/p3.json)"
ERR_CODE2=$(python3 -c 'import json; print(json.load(open("/tmp/p3.json")).get("code",""))' 2>/dev/null)
if [ "$PUR3_HTTP" = "409" ] && [ "$ERR_CODE2" = "ALREADY_SUBSCRIBED" ]; then
  ok "upgrade refuse avec 409 ALREADY_SUBSCRIBED (pas d'upgrade possible)"
else
  ko "upgrade aurait du etre 409 (HTTP=$PUR3_HTTP code=$ERR_CODE2)"
fi

# ─── 7. /me/active = toujours PEL-ONCE (pas de remplacement) ─────────
ME3_HTTP=$(curl -s -o /tmp/me3.json -w '%{http_code}' \
  -H "Authorization: Bearer $U_TOK" "$BASE/api/auth/subscriptions/me/active")
ME3_PLAN=$(python3 -c 'import json; print(json.load(open("/tmp/me3.json")).get("planCode",""))' 2>/dev/null)
ME3_STATUS=$(python3 -c 'import json; print(json.load(open("/tmp/me3.json")).get("status",""))' 2>/dev/null)
echo "/me/active HTTP=$ME3_HTTP plan=$ME3_PLAN status=$ME3_STATUS"
if [ "$ME3_HTTP" = "200" ] && [ "$ME3_PLAN" = "PEL-ONCE" ] && [ "$ME3_STATUS" = "ACTIVE" ]; then
  ok "/me/active reste sur PEL-ONCE ACTIVE (pas de remplacement apres 409)"
else
  ko "/me/active devrait etre PEL-ONCE ACTIVE (HTTP=$ME3_HTTP plan=$ME3_PLAN)"
fi

# ─── 8. Cleanup admin ──────────────────────────────────────────────
echo ""
echo "=== 8. Cleanup : suppression des abonnements et plans du run ==="
docker exec wydad-postgres psql -U wydad -d auth_db -c \
  "DELETE FROM user_subscriptions WHERE plan_id IN ($P1_ID, $P2_ID); DELETE FROM subscription_plans WHERE id IN ($P1_ID, $P2_ID);" \
  >/dev/null 2>&1
D1=$(curl -s -o /dev/null -w '%{http_code}' -X DELETE \
  -H "Authorization: Bearer $A_TOK" -H "X-User-Id: 1" -H "X-User-Email: $ADMIN_EMAIL" -H "X-User-Role: ADMIN" \
  "$BASE/api/admin/subscription-plans/$P1_ID")
echo "DELETE P1 HTTP=$D1 (idempotent — 404 attendu si déjà supprimé via SQL)"
if [ "$D1" = "204" ] || [ "$D1" = "200" ] || [ "$D1" = "404" ]; then ok "cleanup PEL-ONCE ($D1)"; else ko "cleanup PEL-ONCE ($D1)"; fi
D2=$(curl -s -o /dev/null -w '%{http_code}' -X DELETE \
  -H "Authorization: Bearer $A_TOK" -H "X-User-Id: 1" -H "X-User-Email: $ADMIN_EMAIL" -H "X-User-Role: ADMIN" \
  "$BASE/api/admin/subscription-plans/$P2_ID")
echo "DELETE P2 HTTP=$D2 (idempotent)"
if [ "$D2" = "204" ] || [ "$D2" = "200" ] || [ "$D2" = "404" ]; then ok "cleanup PEL-ONCE-2 ($D2)"; else ko "cleanup PEL-ONCE-2 ($D2)"; fi

# ─── Résumé ─────────────────────────────────────────────────────────
echo ""
echo "===================================================="
echo "  REGLE 'UN ABONNEMENT PAR SAISON' — RESULTATS"
echo "  PASS=$PASS  FAIL=$FAIL"
echo "===================================================="
if [ "$FAIL" -gt 0 ]; then
  echo "  ⚠️  CERTAINS TESTS ONT ECHOUE — investiguer les logs ci-dessus"
  exit 1
fi
echo "  ✅ TOUS LES TESTS SONT PASSES"
