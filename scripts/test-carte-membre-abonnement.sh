#!/bin/bash
# Test E2E B.12 + règle « un abonnement par saison » (30/08/2026)
# Carte de membre 100% pilotée par l'abonnement saisonnier (refonte :
# plus de MembershipLevel hardcodé, plus de carte rouge auto-générée
# à l'inscription).
#
# Scénario : un utilisateur neuf (sans abonnement) ne peut PAS obtenir
# de carte de membre. Dès qu'il achète un plan (TEST-CARTE, 100 MAD),
# la carte est générée par le backend. S'il tente de re-acheter
# (TEST-CARTE-2), l'API renvoie 409 ALREADY_SUBSCRIBED : pas
# d'upgrade, pas de remplacement, l'ancien TEST-CARTE reste ACTIF.
#
# Étapes :
#   1. Login user NEUF (sans abonnement) → /api/auth/member-card → 404
#   2. Login ADMIN → créer 2 plans de test (TEST-CARTE, TEST-CARTE-2)
#   3. Login user → POST /api/auth/subscriptions/purchase {TEST-CARTE} → 201 ACTIVE
#   4. GET /api/auth/member-card → 200, planCode=TEST-CARTE, QR présent
#   5. GET /api/auth/attestation → 200, PDF contient TEST-CARTE, pas "Rouge"
#   6. POST /api/auth/subscriptions/purchase {TEST-CARTE-2} → 409 ALREADY_SUBSCRIBED
#      (l'ancien TEST-CARTE reste ACTIVE, pas de remplacement)
#   7. GET /api/auth/member-card → 200, planCode=TEST-CARTE (toujours)
#   8. POST /api/auth/upgrade → 404 (endpoint supprimé)
#   9. Cleanup admin : DELETE les 2 plans (P2 jamais utilisé, cleanup idempotent)
#
# Pré-requis : auth-service expose /api/auth/{member-card,attestation,
# subscriptions/purchase,me/active,upgrade} et /api/admin/subscription-plans.
# Le paiement est SIMULÉ (carte 4242... + OTP 000000 — ChariBaasService
# côté payment-service attend 000000, pas 123456).

set +e

BASE="${WAC_BASE:-http://localhost:8080}"
ADMIN_EMAIL="admin@wac.ma"
# Mot de passe admin : DOIT être fourni via $ADMIN_PASSWORD (sourcing
# .env du serveur qui contient $ADMIN_SEED_PASSWORD). Aucun fallback
# en dur dans le repo — le mot de passe a changé après le 25/08.
: "${ADMIN_PASSWORD:?ADMIN_PASSWORD doit etre defini (source .env du serveur)}"
ADMIN_PASS="$ADMIN_PASSWORD"
USER_PASSWORD="TestPass123!"

# Compteurs de tests
PASS=0
FAIL=0
ok()   { echo "  ✅ $1"; PASS=$((PASS+1)); }
ko()   { echo "  ❌ $1"; FAIL=$((FAIL+1)); }

# ─── 0. Login admin (nécessaire pour cleanup à la fin) ──────────────
echo "=== 0. Login admin ==="
A_LOGIN=$(curl -s -X POST $BASE/api/auth/login -H 'Content-Type: application/json' \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASS\"}")
A_TOK=$(echo "$A_LOGIN" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("accessToken",""))' 2>/dev/null)
A_ROLE=$(echo "$A_LOGIN" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("role","") or " ".join(json.load(sys.stdin).get("roles",[])))' 2>/dev/null)
if [ -z "$A_TOK" ]; then
  echo "ÉCHEC: login admin KO — $A_LOGIN"
  exit 1
fi
echo "admin OK"

# ─── 1. Création d'un user NEUF (sans abonnement) ──────────────────
TS=$(date +%s)
USER_EMAIL="carte-test-$TS@wac.ma"
USER_PHONE="+2126${TS:2:8}"
echo ""
echo "=== 1. Création user $USER_EMAIL (SANS abonnement) ==="
REG=$(curl -s -X POST $BASE/api/auth/register -H 'Content-Type: application/json' \
  -d "{\"email\":\"$USER_EMAIL\",\"password\":\"$USER_PASSWORD\",\"firstName\":\"Carte\",\"lastName\":\"Test\",\"phone\":\"$USER_PHONE\"}")
U_ID=$(echo "$REG" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("id",""))' 2>/dev/null)
if [ -z "$U_ID" ]; then
  echo "ÉCHEC register : $REG"
  exit 1
fi
echo "user id=$U_ID"

# Login user
U_LOGIN=$(curl -s -X POST $BASE/api/auth/login -H 'Content-Type: application/json' \
  -d "{\"email\":\"$USER_EMAIL\",\"password\":\"$USER_PASSWORD\"}")
U_TOK=$(echo "$U_LOGIN" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("accessToken",""))' 2>/dev/null)
U_ROLE=$(echo "$U_LOGIN" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("role","") or " ".join(json.load(sys.stdin).get("roles",[])))' 2>/dev/null)
if [ -z "$U_TOK" ]; then
  echo "ÉCHEC: login user KO (compte probablement EN_ATTENTE — l'admin va le valider pour permettre l'achat)"
fi
echo "user role=$U_ROLE"

# ─── 1b. Si le compte est EN_ATTENTE, on le valide via PATCH
#         /api/auth/admin/accounts/{id}/validate (cf. AuthController
#         ligne 394). On retrouve l'id via /me.
if echo "$U_ROLE" | grep -qi "EN_ATTENTE\|ATTENTE"; then
  echo "  → compte EN_ATTENTE, validation admin via PATCH /admin/accounts/{id}/validate"
  ME_HTTP=$(curl -s -o /tmp/me.json -w '%{http_code}' \
    -H "Authorization: Bearer $U_TOK" \
    "$BASE/api/auth/me")
  U_NUMERIC_ID=$(python3 -c 'import json; print(json.load(open("/tmp/me.json")).get("id",""))' 2>/dev/null)
  if [ -n "$U_NUMERIC_ID" ]; then
    curl -s -X PATCH "$BASE/api/auth/admin/accounts/$U_NUMERIC_ID/validate" \
      -H "Authorization: Bearer $A_TOK" -H "X-User-Id: 1" -H "X-User-Email: $ADMIN_EMAIL" -H "X-User-Role: ADMIN" \
      >/dev/null 2>&1
    # Re-login pour récup un token avec le nouveau statut
    U_LOGIN=$(curl -s -X POST $BASE/api/auth/login -H 'Content-Type: application/json' \
      -d "{\"email\":\"$USER_EMAIL\",\"password\":\"$USER_PASSWORD\"}")
    U_TOK=$(echo "$U_LOGIN" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("accessToken",""))' 2>/dev/null)
    U_ROLE=$(echo "$U_LOGIN" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("role","") or " ".join(json.load(sys.stdin).get("roles",[])))' 2>/dev/null)
    echo "  → user validé, nouveau role=$U_ROLE"
  else
    echo "  ⚠️  impossible de récupérer l'id du user pour validation"
  fi
fi

# ─── 1c. /api/auth/member-card SANS abonnement → 404 attendu ───────
echo ""
echo "=== 1c. /member-card SANS abonnement → doit renvoyer 404 ==="
CARD_HTTP=$(curl -s -o /tmp/card.json -w '%{http_code}' \
  -H "Authorization: Bearer $U_TOK" -H "X-User-Id: $U_ID" -H "X-User-Email: $USER_EMAIL" -H "X-User-Role: $U_ROLE" \
  "$BASE/api/auth/member-card?email=$USER_EMAIL")
echo "HTTP=$CARD_HTTP body=$(cat /tmp/card.json)"
if [ "$CARD_HTTP" = "404" ]; then
  ok "carte de membre refusee sans abonnement (404)"
else
  ko "/member-card devrait etre 404 sans abonnement (HTTP=$CARD_HTTP)"
fi

# ─── 1d. /me/active SANS abonnement → 204 NoContent ────────────────
ME_HTTP=$(curl -s -o /tmp/me.json -w '%{http_code}' \
  -H "Authorization: Bearer $U_TOK" -H "X-User-Id: $U_ID" -H "X-User-Email: $USER_EMAIL" \
  "$BASE/api/auth/subscriptions/me/active")
echo "/me/active HTTP=$ME_HTTP"
if [ "$ME_HTTP" = "204" ] || [ "$ME_HTTP" = "404" ]; then
  ok "/me/active vide (HTTP=$ME_HTTP)"
else
  ko "/me/active devrait etre 204/404 (HTTP=$ME_HTTP)"
fi

# ─── 1e. Cleanup préventif : supprimer d'éventuels plans de test
#         reliques d'un précédent run (le script n'est pas réentrant si
#         la session précédente a planté après la création).
echo ""
echo "=== 1e. Cleanup préventif des plans TEST-CARTE(-2) éventuels ==="
docker exec wydad-postgres psql -U wydad -d auth_db -c \
  "DELETE FROM user_subscriptions WHERE plan_id IN (SELECT id FROM subscription_plans WHERE code IN ('TEST-CARTE','TEST-CARTE-2')); DELETE FROM subscription_plans WHERE code IN ('TEST-CARTE','TEST-CARTE-2');" \
  >/dev/null 2>&1
echo "  (cleanup OK ou aucun plan à supprimer)"
echo ""
echo "=== 2. Admin cree TEST-CARTE (100/80) et TEST-CARTE-2 (200/160) ==="
P1=$(curl -s -X POST "$BASE/api/admin/subscription-plans" \
  -H "Authorization: Bearer $A_TOK" -H "X-User-Id: 1" -H "X-User-Email: $ADMIN_EMAIL" -H "X-User-Role: ADMIN" \
  -H 'Content-Type: application/json' \
  -d '{"code":"TEST-CARTE","name":"Plan Test Carte","regularPrice":100,"adherentPrice":80,"isActive":true,"displayOrder":99,"exceptionalPriority":false,"season":"2026-2027"}')
P1_ID=$(echo "$P1" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("id",""))' 2>/dev/null)
echo "TEST-CARTE id=$P1_ID"

P2=$(curl -s -X POST "$BASE/api/admin/subscription-plans" \
  -H "Authorization: Bearer $A_TOK" -H "X-User-Id: 1" -H "X-User-Email: $ADMIN_EMAIL" -H "X-User-Role: ADMIN" \
  -H 'Content-Type: application/json' \
  -d '{"code":"TEST-CARTE-2","name":"Plan Test Carte 2","regularPrice":200,"adherentPrice":160,"isActive":true,"displayOrder":98,"exceptionalPriority":false,"season":"2026-2027"}')
P2_ID=$(echo "$P2" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("id",""))' 2>/dev/null)
echo "TEST-CARTE-2 id=$P2_ID"

if [ -n "$P1_ID" ] && [ -n "$P2_ID" ]; then
  ok "creation des 2 plans par admin"
else
  ko "creation plans KO (P1=$P1 P2=$P2)"
  echo "On continue pour debug mais les tests d'achat vont echouer"
fi

# ─── 3. Achat TEST-CARTE → 201 ACTIVE ───────────────────────────────
echo ""
echo "=== 3. Achat TEST-CARTE (100 MAD) ==="
PUR1_HTTP=$(curl -s -o /tmp/p1.json -w '%{http_code}' -X POST $BASE/api/auth/subscriptions/purchase \
  -H "Authorization: Bearer $U_TOK" -H "X-User-Id: $U_ID" -H "X-User-Email: $USER_EMAIL" \
  -H 'Content-Type: application/json' \
  -d '{"planCode":"TEST-CARTE","cardNumber":"4242424242424242","expiryDate":"12/29","cvv":"123","otp":"000000"}')
echo "HTTP=$PUR1_HTTP body=$(cat /tmp/p1.json)"
STATUS=$(python3 -c 'import json; print(json.load(open("/tmp/p1.json")).get("status",""))' 2>/dev/null)
PLAN=$(python3 -c 'import json; print(json.load(open("/tmp/p1.json")).get("planCode",""))' 2>/dev/null)
PAID=$(python3 -c 'import json; print(json.load(open("/tmp/p1.json")).get("paidAmount",0))' 2>/dev/null)
if [ "$PUR1_HTTP" = "201" ] && [ "$STATUS" = "ACTIVE" ] && [ "$PLAN" = "TEST-CARTE" ] && [ "$PAID" = "100.0" ]; then
  ok "achat 1 OK (201 ACTIVE plan=TEST-CARTE paid=100)"
else
  ko "achat 1 KO (HTTP=$PUR1_HTTP status=$STATUS plan=$PLAN paid=$PAID)"
fi

# ─── 4. /member-card → 200 planCode=TEST-CARTE, QR present ─────────
echo ""
echo "=== 4. /member-card apres achat 1 ==="
CARD_HTTP=$(curl -s -o /tmp/card.json -w '%{http_code}' \
  -H "Authorization: Bearer $U_TOK" -H "X-User-Id: $U_ID" -H "X-User-Email: $USER_EMAIL" -H "X-User-Role: $U_ROLE" \
  "$BASE/api/auth/member-card?email=$USER_EMAIL")
echo "HTTP=$CARD_HTTP body=$(head -c 400 /tmp/card.json)"
if [ "$CARD_HTTP" = "200" ]; then
  PLAN=$(python3 -c 'import json; print(json.load(open("/tmp/card.json")).get("planCode",""))' 2>/dev/null)
  PNAME=$(python3 -c 'import json; print(json.load(open("/tmp/card.json")).get("planName",""))' 2>/dev/null)
  QR_LEN=$(python3 -c 'import json; print(len(json.load(open("/tmp/card.json")).get("qrCodeBase64","") or ""))' 2>/dev/null)
  SEASON=$(python3 -c 'import json; print(json.load(open("/tmp/card.json")).get("season",""))' 2>/dev/null)
  FIRSTNAME=$(python3 -c 'import json; print(json.load(open("/tmp/card.json")).get("firstName",""))' 2>/dev/null)
  echo "  → planCode=$PLAN planName=$PNAME season=$SEASON firstName=$FIRSTNAME qrLen=$QR_LEN"
  if [ "$PLAN" = "TEST-CARTE" ] && [ "$QR_LEN" -gt 100 ] && [ "$FIRSTNAME" = "Carte" ]; then
    ok "carte de membre generee selon l'abonnement (planCode=TEST-CARTE, QR > 100 chars, prenom OK)"
  else
    ko "carte incoherente: plan=$PLAN qr=$QR_LEN firstName=$FIRSTNAME"
  fi
else
  ko "/member-card devrait etre 200 apres achat (HTTP=$CARD_HTTP)"
fi

# ─── 5. /attestation → 200 PDF, contient TEST-CARTE pas Rouge ──────
echo ""
echo "=== 5. /attestation PDF ==="
ATT_HTTP=$(curl -s -o /tmp/attestation.pdf -w '%{http_code}' \
  -H "Authorization: Bearer $U_TOK" -H "X-User-Id: $U_ID" -H "X-User-Email: $USER_EMAIL" -H "X-User-Role: $U_ROLE" \
  "$BASE/api/auth/attestation?email=$USER_EMAIL")
PDF_SIZE=$(stat -c%s /tmp/attestation.pdf 2>/dev/null || wc -c < /tmp/attestation.pdf)
PDF_HEADER=$(head -c 4 /tmp/attestation.pdf)
echo "HTTP=$ATT_HTTP size=$PDF_SIZE header=$PDF_HEADER"
if [ "$ATT_HTTP" = "200" ] && [ "$PDF_HEADER" = "%PDF" ] && [ "$PDF_SIZE" -gt 1000 ]; then
  ok "PDF genere ($PDF_SIZE bytes, header %PDF OK)"
  # On extrait le texte du PDF (pdftotext est dispo sur VM) pour vérifier
  # que le planCode TEST-CARTE est bien là et que "Rouge" (legacy
  # MembershipLevel) ne l'est plus.
  if command -v pdftotext >/dev/null 2>&1; then
    PDFTEXT=$(pdftotext /tmp/attestation.pdf - 2>/dev/null)
    if echo "$PDFTEXT" | grep -q "TEST-CARTE"; then
      ok "PDF mentionne le planCode TEST-CARTE"
    else
      ko "PDF ne mentionne pas TEST-CARTE (texte: $(echo "$PDFTEXT" | head -c 200))"
    fi
    if echo "$PDFTEXT" | grep -qi "rouge\|ROUGEMEMBRE\|Rouge " ; then
      ko "PDF mentionne encore 'Rouge' (MembershipLevel legacy detecte)"
    else
      ok "PDF ne mentionne plus le MembershipLevel legacy 'Rouge'"
    fi
  else
    echo "  (pdftotext absent, skip verif contenu PDF)"
  fi
else
  ko "PDF KO (HTTP=$ATT_HTTP size=$PDF_SIZE)"
fi

# ─── 6. Achat TEST-CARTE-2 → 409 ALREADY_SUBSCRIBED (règle « un par saison »)
#         Avant (B.12) : 201 + ancien REPLACED, nouveau ACTIVE.
#         Maintenant (30/08/2026) : 409, l'ancien TEST-CARTE reste ACTIF,
#         aucun débit, pas d'upgrade possible.
echo ""
echo "=== 6. Achat TEST-CARTE-2 (200 MAD) — doit etre 409 ALREADY_SUBSCRIBED ==="
PUR2_HTTP=$(curl -s -o /tmp/p2.json -w '%{http_code}' -X POST $BASE/api/auth/subscriptions/purchase \
  -H "Authorization: Bearer $U_TOK" -H "X-User-Id: $U_ID" -H "X-User-Email: $USER_EMAIL" \
  -H 'Content-Type: application/json' \
  -d '{"planCode":"TEST-CARTE-2","cardNumber":"4242424242424242","expiryDate":"12/29","cvv":"123","otp":"000000"}')
echo "HTTP=$PUR2_HTTP body=$(cat /tmp/p2.json)"
ERR_CODE=$(python3 -c 'import json; print(json.load(open("/tmp/p2.json")).get("code",""))' 2>/dev/null)
ERR_MSG=$(python3 -c 'import json; print(json.load(open("/tmp/p2.json")).get("message",""))' 2>/dev/null)
if [ "$PUR2_HTTP" = "409" ] && [ "$ERR_CODE" = "ALREADY_SUBSCRIBED" ]; then
  ok "2e achat refuse avec 409 ALREADY_SUBSCRIBED"
  # Verif que le message mentionne bien la saison et la règle
  if echo "$ERR_MSG" | grep -q "déjà un abonnement" && echo "$ERR_MSG" | grep -q "saison"; then
    ok "message 409 explicite (saison + 'deja un abonnement')"
  else
    ko "message 409 peu clair: $ERR_MSG"
  fi
else
  ko "2e achat aurait du etre 409 ALREADY_SUBSCRIBED (HTTP=$PUR2_HTTP code=$ERR_CODE)"
fi

# /me/active doit toujours retourner TEST-CARTE (l'ancien, pas remplacé)
ME2_HTTP=$(curl -s -o /tmp/me2.json -w '%{http_code}' \
  -H "Authorization: Bearer $U_TOK" -H "X-User-Id: $U_ID" -H "X-User-Email: $USER_EMAIL" \
  "$BASE/api/auth/subscriptions/me/active")
ME2_PLAN=$(python3 -c 'import json; print(json.load(open("/tmp/me2.json")).get("planCode",""))' 2>/dev/null)
ME2_STATUS=$(python3 -c 'import json; print(json.load(open("/tmp/me2.json")).get("status",""))' 2>/dev/null)
echo "/me/active HTTP=$ME2_HTTP plan=$ME2_PLAN status=$ME2_STATUS"
if [ "$ME2_HTTP" = "200" ] && [ "$ME2_PLAN" = "TEST-CARTE" ] && [ "$ME2_STATUS" = "ACTIVE" ]; then
  ok "/me/active reste sur TEST-CARTE ACTIVE (le 2e achat a ete refuse, pas de remplacement)"
else
  ko "/me/active devrait etre TEST-CARTE ACTIVE (HTTP=$ME2_HTTP plan=$ME2_PLAN)"
fi

# ─── 7. /member-card → planCode=TEST-CARTE (toujours) ───────────────
echo ""
echo "=== 7. /member-card apres tentative 2 — doit toujours afficher TEST-CARTE ==="
CARD2_HTTP=$(curl -s -o /tmp/card2.json -w '%{http_code}' \
  -H "Authorization: Bearer $U_TOK" -H "X-User-Id: $U_ID" -H "X-User-Email: $USER_EMAIL" -H "X-User-Role: $U_ROLE" \
  "$BASE/api/auth/member-card?email=$USER_EMAIL")
PLAN2=$(python3 -c 'import json; print(json.load(open("/tmp/card2.json")).get("planCode",""))' 2>/dev/null)
echo "HTTP=$CARD2_HTTP planCode=$PLAN2"
if [ "$CARD2_HTTP" = "200" ] && [ "$PLAN2" = "TEST-CARTE" ]; then
  ok "carte de membre reflete toujours TEST-CARTE (2e achat refuse, pas de switch)"
else
  ko "/member-card devrait etre TEST-CARTE (HTTP=$CARD2_HTTP plan=$PLAN2)"
fi

# ─── 8. /api/auth/upgrade → 404 (supprime) ─────────────────────────
echo ""
echo "=== 8. POST /api/auth/upgrade — doit etre 404 (endpoint supprime) ==="
UP_HTTP=$(curl -s -o /tmp/up.json -w '%{http_code}' -X POST $BASE/api/auth/upgrade \
  -H "Authorization: Bearer $U_TOK" -H "X-User-Id: $U_ID" -H "X-User-Email: $USER_EMAIL" \
  -H 'Content-Type: application/json' -d '{"membershipLevel":"OR"}')
echo "HTTP=$UP_HTTP body=$(cat /tmp/up.json)"
if [ "$UP_HTTP" = "404" ]; then
  ok "endpoint /api/auth/upgrade bien supprime (404)"
else
  ko "/api/auth/upgrade devrait etre 404 (HTTP=$UP_HTTP)"
fi

# ─── 9. Cleanup : admin supprime les 2 plans ────────────────────────
#         (on supprime d'abord les abonnements du run, sinon 409 FK
#         car user_subscriptions.plan_id référence subscription_plans.id)
echo ""
echo "=== 9. Cleanup : suppression des abonnements et plans du run ==="
docker exec wydad-postgres psql -U wydad -d auth_db -c \
  "DELETE FROM user_subscriptions WHERE plan_id IN ($P1_ID, $P2_ID); DELETE FROM subscription_plans WHERE id IN ($P1_ID, $P2_ID);" \
  >/dev/null 2>&1
D1=$(curl -s -o /dev/null -w '%{http_code}' -X DELETE \
  -H "Authorization: Bearer $A_TOK" -H "X-User-Id: 1" -H "X-User-Email: $ADMIN_EMAIL" -H "X-User-Role: ADMIN" \
  "$BASE/api/admin/subscription-plans/$P1_ID")
echo "DELETE P1 HTTP=$D1 (idempotent — 404 attendu si déjà supprimé via SQL)"
if [ "$D1" = "204" ] || [ "$D1" = "200" ] || [ "$D1" = "404" ]; then ok "cleanup TEST-CARTE ($D1)"; else ko "cleanup TEST-CARTE ($D1)"; fi
D2=$(curl -s -o /dev/null -w '%{http_code}' -X DELETE \
  -H "Authorization: Bearer $A_TOK" -H "X-User-Id: 1" -H "X-User-Email: $ADMIN_EMAIL" -H "X-User-Role: ADMIN" \
  "$BASE/api/admin/subscription-plans/$P2_ID")
echo "DELETE P2 HTTP=$D2 (idempotent — 404 attendu si déjà supprimé via SQL)"
if [ "$D2" = "204" ] || [ "$D2" = "200" ] || [ "$D2" = "404" ]; then ok "cleanup TEST-CARTE-2 ($D2)"; else ko "cleanup TEST-CARTE-2 ($D2)"; fi

# ─── Résumé ─────────────────────────────────────────────────────────
echo ""
echo "===================================================="
echo "  CARTE MEMBRE PILOTEE PAR ABONNEMENT — RESULTATS"
echo "  PASS=$PASS  FAIL=$FAIL"
echo "===================================================="
if [ "$FAIL" -gt 0 ]; then
  echo "  ⚠️  CERTAINS TESTS ONT ECHOUE — investiguer les logs ci-dessus"
  exit 1
fi
echo "  ✅ TOUS LES TESTS SONT PASSES"
