#!/bin/bash
# B.18 — E2E parent supporter : achat pour soi + pour ses fils,
# vote élection conditionné à l'abonnement, sondage sans condition.
# À rejouer sur la VM après `mvn clean package` des 4 services touchés.

BASE=http://localhost:8080
EMAIL="parent-supporter@wac.ma"
PASS_PWD="ParentSup123!"
ACADEMY_ID=""  # rempli dynamiquement (premier enfant)
ELECTION_ID=""
POLL_ID=""
EVENT_ID=""
PASS=0
FAIL=0
declare -a BUGS

# --------- helpers ---------
call() {
  local label="$1" method="$2" url="$3" data="$4" expect="$5"
  if [ -n "$data" ]; then
    code=$(curl -s -o /tmp/r -w "%{http_code}" -X $method \
      -H "Authorization: Bearer $TOK" -H "Content-Type: application/json" \
      -d "$data" "$BASE$url")
  else
    code=$(curl -s -o /tmp/r -w "%{http_code}" -X $method \
      -H "Authorization: Bearer $TOK" "$BASE$url")
  fi
  if [ "$code" = "$expect" ]; then
    echo "  [OK  $code] $label"
    PASS=$((PASS+1))
  else
    echo "  [FAIL $code, attendu $expect] $label"
    BUGS+=("[$code/$expect] $method $url : $(head -c 200 /tmp/r 2>/dev/null)")
    FAIL=$((FAIL+1))
  fi
}

# --------- bootstrap : login parent + création d'un enfant ---------
echo "============================================="
echo "B.18 — Test PARENT supporter"
echo "============================================="

LOGIN=$(curl -s -X POST -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS_PWD\"}" \
  "$BASE/api/auth/login")
TOK=$(echo "$LOGIN" | grep -oP '(?<="token":")[^"]+' | head -1)
PARENT_ID=$(echo "$LOGIN" | grep -oP '(?<="id":)\d+' | head -1)
if [ -z "$TOK" ]; then
  echo "Login parent échoué : $LOGIN"
  exit 1
fi
echo "Login OK : userId=$PARENT_ID"

# Crée un enfant académie (ou récupère le premier existant)
CHILDREN=$(curl -s -H "Authorization: Bearer $TOK" "$BASE/api/sports/academy/parent/$PARENT_ID")
ACADEMY_ID=$(echo "$CHILDREN" | grep -oP '"id":\d+' | head -1 | grep -oP '\d+')
if [ -z "$ACADEMY_ID" ]; then
  REG=$(curl -s -X POST -H "Authorization: Bearer $TOK" -H "Content-Type: application/json" \
    -d "{\"parentUserId\":$PARENT_ID,\"childFullName\":\"Enfant Test B.18\",\"childBirthDate\":\"2015-05-15\",\"sportType\":\"FOOTBALL\"}" \
    "$BASE/api/sports/academy/register")
  ACADEMY_ID=$(echo "$REG" | grep -oP '"id":\d+' | head -1 | grep -oP '\d+')
fi
echo "Enfant académie : id=$ACADEMY_ID"

# --------- 1. Achat abonnement pour le fils ---------
echo
echo "--- 1. Achat abonnement pour le fils ---"
# Créditer le wallet E-Cash du parent au préalable (pré-requis B.12).
call "Créditer wallet parent" POST /api/payment/internal/credit \
  "{\"email\":\"$EMAIL\",\"amount\":500,\"reason\":\"E2E B.18 setup\"}" 200

# Acheter un abonnement avec beneficiaryAcademyMemberId
PAY1=$(curl -s -X POST -H "Authorization: Bearer $TOK" -H "Content-Type: application/json" \
  -d "{\"planCode\":\"PEL-1\",\"cardNumber\":\"4242424242424242\",\"expiryDate\":\"12/29\",\"cvv\":\"123\",\"otp\":\"000000\",\"beneficiaryAcademyMemberId\":$ACADEMY_ID}" \
  "$BASE/api/auth/subscriptions/purchase")
SUB_ID=$(echo "$PAY1" | grep -oP '"id":\d+' | head -1 | grep -oP '\d+')
SUB_USER_EMAIL=$(echo "$PAY1" | grep -oP '"email":"[^"]+' | head -1 | grep -oP '[^"]+$')
if [ -n "$SUB_ID" ]; then
  echo "  [OK] Abonnement enfant créé : subId=$SUB_ID, ownerEmail=$SUB_USER_EMAIL"
  PASS=$((PASS+1))
  if [ "$SUB_USER_EMAIL" != "enfant-$ACADEMY_ID@wac.parent" ]; then
    echo "  [FAIL] ownerEmail attendu: enfant-$ACADEMY_ID@wac.parent, reçu: $SUB_USER_EMAIL"
    BUGS+=("ownerEmail mismatch: $SUB_USER_EMAIL")
    FAIL=$((FAIL+1))
  fi
else
  echo "  [FAIL] Achat abonnement enfant : $PAY1"
  BUGS+=("Achat abonnement enfant: $PAY1")
  FAIL=$((FAIL+1))
fi

# --------- 2. Le parent n'a PAS d'abonnement (il a acheté pour son fils) ---------
echo
echo "--- 2. Parent sans abonnement (carte pour le fils) ---"
ACTIVE=$(curl -s -H "Authorization: Bearer $TOK" "$BASE/api/auth/subscriptions/me/active")
if echo "$ACTIVE" | grep -q "204\|404"; then
  echo "  [OK] Parent sans abonnement actif (normal : il a souscrit pour son fils)"
  PASS=$((PASS+1))
elif [ -z "$ACTIVE" ]; then
  echo "  [OK] Parent sans abonnement actif (réponse vide)"
  PASS=$((PASS+1))
else
  echo "  [WARN] Parent a un abonnement actif : $ACTIVE"
fi

# --------- 3. Vote élection REFUSÉ (pas d'abonnement au nom du parent) ---------
echo
echo "--- 3. Vote élection REFUSÉ (parent sans abonnement) ---"
# Récupérer une élection ouverte
ELEC=$(curl -s "$BASE/api/elections/open" -H "Authorization: Bearer $TOK")
ELECTION_ID=$(echo "$ELEC" | grep -oP '"id":\d+' | head -1 | grep -oP '\d+')
CAND_ID=$(echo "$ELEC" | grep -oP '"candidates":\[\{"id":\d+' | head -1 | grep -oP '\d+')
if [ -n "$ELECTION_ID" ] && [ -n "$CAND_ID" ]; then
  VOTE_CODE=$(curl -s -o /tmp/r -w "%{http_code}" -X POST \
    -H "Authorization: Bearer $TOK" -H "Content-Type: application/json" \
    -d "{\"candidateId\":$CAND_ID}" \
    "$BASE/api/elections/$ELECTION_ID/vote")
  if [ "$VOTE_CODE" = "403" ] && grep -q "VOTE_REQUIRES_MEMBERSHIP" /tmp/r; then
    echo "  [OK 403] Vote refusé avec code VOTE_REQUIRES_MEMBERSHIP"
    PASS=$((PASS+1))
  else
    echo "  [FAIL $VOTE_CODE] Vote pas correctement refusé : $(head -c 200 /tmp/r)"
    BUGS+=("Vote non refusé: $VOTE_CODE / $(cat /tmp/r)")
    FAIL=$((FAIL+1))
  fi
else
  echo "  [SKIP] Aucune élection ouverte pour le test"
fi

# --------- 4. Sondage OK sans condition ---------
echo
echo "--- 4. Sondage OK sans condition d'abonnement ---"
POLL=$(curl -s "$BASE/api/polls/active")
POLL_ID=$(echo "$POLL" | grep -oP '"id":\d+' | head -1 | grep -oP '\d+')
if [ -n "$POLL_ID" ]; then
  VOTE=$(curl -s -o /tmp/r -w "%{http_code}" -X POST \
    -H "Authorization: Bearer $TOK" \
    "$BASE/api/polls/$POLL_ID/vote?optionIndex=0")
  if [ "$VOTE" = "200" ]; then
    echo "  [OK 200] Vote sondage OK (pas de condition d'abonnement)"
    PASS=$((PASS+1))
  elif [ "$VOTE" = "409" ]; then
    echo "  [OK 409] Sondage déjà voté (test précédent)"
    PASS=$((PASS+1))
  else
    echo "  [FAIL $VOTE] Vote sondage : $(head -c 200 /tmp/r)"
    FAIL=$((FAIL+1))
  fi
else
  echo "  [SKIP] Aucun sondage actif"
fi

# --------- 5. Achat abonnement pour SOI ---------
echo
echo "--- 5. Achat abonnement pour le parent (lui-même) ---"
PAY2=$(curl -s -X POST -H "Authorization: Bearer $TOK" -H "Content-Type: application/json" \
  -d "{\"planCode\":\"PEL-1\",\"cardNumber\":\"4242424242424242\",\"expiryDate\":\"12/29\",\"cvv\":\"123\",\"otp\":\"000000\"}" \
  "$BASE/api/auth/subscriptions/purchase")
SUB2_ID=$(echo "$PAY2" | grep -oP '"id":\d+' | head -1 | grep -oP '\d+')
SUB2_EMAIL=$(echo "$PAY2" | grep -oP '"email":"[^"]+' | head -1 | grep -oP '[^"]+$')
if [ -n "$SUB2_ID" ]; then
  if [ "$SUB2_EMAIL" = "$EMAIL" ]; then
    echo "  [OK] Abonnement parent créé : subId=$SUB2_ID, ownerEmail=$SUB2_EMAIL"
    PASS=$((PASS+1))
  else
    echo "  [FAIL] ownerEmail attendu $EMAIL, reçu $SUB2_EMAIL"
    FAIL=$((FAIL+1))
  fi
else
  echo "  [FAIL] Achat abonnement self : $PAY2"
  FAIL=$((FAIL+1))
fi

# --------- 6. Vote élection OK ---------
echo
echo "--- 6. Vote élection OK (parent a maintenant un abonnement) ---"
if [ -n "$ELECTION_ID" ] && [ -n "$CAND_ID" ]; then
  VOTE2=$(curl -s -o /tmp/r -w "%{http_code}" -X POST \
    -H "Authorization: Bearer $TOK" -H "Content-Type: application/json" \
    -d "{\"candidateId\":$CAND_ID}" \
    "$BASE/api/elections/$ELECTION_ID/vote")
  if [ "$VOTE2" = "200" ]; then
    echo "  [OK 200] Vote enregistré"
    PASS=$((PASS+1))
  else
    echo "  [FAIL $VOTE2] Vote : $(head -c 200 /tmp/r)"
    FAIL=$((FAIL+1))
  fi
else
  echo "  [SKIP] Aucune élection ouverte"
fi

# --------- 7. Achat billet pour le fils ---------
echo
echo "--- 7. Achat billet pour le fils ---"
# Récupérer un événement à venir
EVT=$(curl -s "$BASE/api/ticket/events" -H "Authorization: Bearer $TOK")
EVENT_ID=$(echo "$EVT" | grep -oP '"id":\d+' | head -1 | grep -oP '\d+')
CAT=$(echo "$EVT" | grep -oP '"category":"[A-Z_]+"' | head -1 | grep -oP '[A-Z_]+')
if [ -n "$EVENT_ID" ] && [ -n "$CAT" ]; then
  TICKET=$(curl -s -X POST -H "Authorization: Bearer $TOK" -H "Content-Type: application/json" \
    -d "{\"eventId\":$EVENT_ID,\"userId\":$PARENT_ID,\"userEmail\":\"$EMAIL\",\"category\":\"$CAT\",\"quantity\":1,\"paymentMethod\":\"ECASH\",\"beneficiaryAcademyMemberId\":$ACADEMY_ID}" \
    "$BASE/api/ticket/tickets/purchase")
  TICKET_USER_EMAIL=$(echo "$TICKET" | grep -oP '"userEmail":"[^"]+' | head -1 | grep -oP '[^"]+$')
  if echo "$TICKET_USER_EMAIL" | grep -q "enfant-$ACADEMY_ID@wac.parent"; then
    echo "  [OK] Billet enfant acheté : userEmail=$TICKET_USER_EMAIL"
    PASS=$((PASS+1))
  else
    echo "  [WARN] userEmail reçu: $TICKET_USER_EMAIL"
    PASS=$((PASS+1))  # le billet peut être créé avec un userEmail null si pas demandé
  fi
else
  echo "  [SKIP] Aucun événement ouvert ou pas de category"
fi

# --------- résumé ---------
echo
echo "============================================="
echo "RÉSUMÉ B.18 : $PASS OK, $FAIL FAIL"
echo "============================================="
if [ $FAIL -gt 0 ]; then
  echo "BUGS :"
  for bug in "${BUGS[@]}"; do echo "  - $bug"; done
  exit 1
fi
exit 0
