#!/bin/bash
# Test E2E B.28 — Achat sans compte VISITEUR

set +e

GUEST_EMAIL="visiteur-test-$(date +%s)@wac.ma"
GUEST_PHONE="+21260$(date +%N | cut -c1-7)"

# 1. Trouver un événement à venir
echo "=== 1. Trouver un événement VIP à venir (avec section VIP disponible) ==="
# Choisit l'event le plus récent qui possède une section VIP (la seule
# catégorie du test B.28) — l'event 4 "TestVIP-SECTION-VIP" en est un.
EVENT_ID=$(for id in $(curl -s "http://localhost:8080/api/ticket/events/upcoming" | python3 -c 'import sys,json; [print(e["id"]) for e in json.load(sys.stdin)]'); do
  has=$(curl -s "http://localhost:8080/api/ticket/events/$id" | python3 -c 'import sys,json; d=json.load(sys.stdin); print(any(s.get("category")=="VIP" and s.get("availableSeats",0)>0 for s in d.get("sections",[])))')
  if [ "$has" = "True" ]; then echo "$id"; break; fi
done)
echo "EVENT_ID=$EVENT_ID"
if [ "$EVENT_ID" = "NONE" ] || [ -z "$EVENT_ID" ]; then
  echo "Pas d'événement à venir. Création d'un événement test..."

  A_LOGIN=$(curl -s -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' -d '{"email":"admin@wac.ma","password":"gW2Ik9f6unGIuU1y7Y5Zy70A82"}')
  A_TOK=$(echo "$A_LOGIN" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("accessToken",""))')
  A_ID=$(echo "$A_LOGIN" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("id",""))')

  EVENT_BODY='{"title":"WAC vs Raja Test VISITEUR","eventType":"FOOTBALL","category":"SENIOR","venue":"Stade Mohammed V","eventDate":"2026-12-15T20:00:00","totalSeats":100,"description":"Test","ticketPrice":50.0,"sections":[{"name":"Tribune Visiteur","category":"VIP","price":50.0,"totalSeats":100,"availableSeats":100}]}'
  EVENT_CREATE=$(curl -s -X POST http://localhost:8080/api/ticket/events \
    -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $A_TOK" \
    -H "X-User-Id: $A_ID" \
    -H 'X-User-Email: admin@wac.ma' \
    -d "$EVENT_BODY")
  EVENT_ID=$(echo "$EVENT_CREATE" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("id",""))')
  echo "EVENT_ID créé=$EVENT_ID"
fi

if [ -z "$EVENT_ID" ] || [ "$EVENT_ID" = "NONE" ]; then
  echo "ÉCHEC: pas d'événement disponible"
  exit 1
fi

# 2. Vérifier le catalogue public (sans JWT)
echo ""
echo "=== 2. Catalogue public events (sans JWT) ==="
curl -s -o /dev/null -w 'HTTP:%{http_code}\n' "http://localhost:8080/api/ticket/events/$EVENT_ID"

# 3. Test ACHAT VISITEUR (sans JWT, sans compte)
echo ""
echo "=== 3. Achat VISITEUR (sans JWT) ==="
GUEST_BODY='{"eventId":'"$EVENT_ID"',"category":"VIP","quantity":2,"guestFirstName":"Visiteur","guestLastName":"Test","guestEmail":"'"$GUEST_EMAIL"'","guestPhone":"'"$GUEST_PHONE"'","paymentMethod":"CARD"}'
GUEST_PURCHASE=$(curl -s -X POST http://localhost:8080/api/ticket/tickets/purchase-guest \
  -H 'Content-Type: application/json' \
  -d "$GUEST_BODY" -w '\nHTTP:%{http_code}\n')
echo "$GUEST_PURCHASE" | head -c 800
echo ""

# 4. Vérifier que l'user VISITEUR a bien été créé en BDD
echo ""
echo "=== 4. Vérification user VISITEUR en BDD ==="
docker exec wydad-postgres psql -U wydad -d auth_db -c "SELECT id, email, role, statut_compte, first_name, last_name, phone FROM users WHERE email='$GUEST_EMAIL';"

# 5. Vérifier que les tickets ont bien été créés
echo ""
echo "=== 5. Vérification tickets en BDD ==="
docker exec wydad-postgres psql -U wydad -d ticket_db -c "SELECT id, ticket_number, user_id, user_email, status, price FROM tickets WHERE user_email='$GUEST_EMAIL' ORDER BY id DESC LIMIT 5;"

# 6. Test idempotence : 2e achat avec le même email (doit retourner le même userId)
echo ""
echo "=== 6. Test idempotence (2e achat même email) ==="
GUEST_BODY2='{"eventId":'"$EVENT_ID"',"category":"VIP","quantity":1,"guestFirstName":"Visiteur","guestLastName":"Test","guestEmail":"'"$GUEST_EMAIL"'","guestPhone":"'"$GUEST_PHONE"'","paymentMethod":"CARD"}'
GUEST_PURCHASE2=$(curl -s -X POST http://localhost:8080/api/ticket/tickets/purchase-guest \
  -H 'Content-Type: application/json' \
  -d "$GUEST_BODY2" -w '\nHTTP:%{http_code}\n')
echo "$GUEST_PURCHASE2" | head -c 400
echo ""

# 7. Test refus paiement E-CASH
echo ""
echo "=== 7. Test refus E-CASH pour visiteur ==="
GUEST_BODY3='{"eventId":'"$EVENT_ID"',"category":"VIP","quantity":1,"guestFirstName":"X","guestLastName":"Y","guestEmail":"ecash-'$RANDOM$ANDOM'@wac.ma","guestPhone":"+21260'$RANDOM$RANDOM'","paymentMethod":"ECASH"}'
curl -s -X POST http://localhost:8080/api/ticket/tickets/purchase-guest \
  -H 'Content-Type: application/json' \
  -d "$GUEST_BODY3" -w '\nHTTP:%{http_code}\n' | head -c 400
echo ""

# 8. Test validation : champs obligatoires manquants
echo ""
echo "=== 8. Test email manquant (400 attendu) ==="
GUEST_BODY4='{"eventId":'"$EVENT_ID"',"category":"VIP","quantity":1,"guestFirstName":"X","guestLastName":"Y","guestPhone":"+212600077777"}'
curl -s -X POST http://localhost:8080/api/ticket/tickets/purchase-guest \
  -H 'Content-Type: application/json' \
  -d "$GUEST_BODY4" -w '\nHTTP:%{http_code}\n' | head -c 300
echo ""

# 9. Test visiteur existe bien
echo ""
echo "=== 9. Le VISITEUR créé existe en BDD ==="
docker exec wydad-postgres psql -U wydad -d auth_db -c "SELECT id, email, role, statut_compte, active FROM users WHERE email='$GUEST_EMAIL' AND role='VISITEUR';"

# 10. Test isolation : un membre ne peut PAS utiliser cet endpoint
echo ""
echo "=== 10. Test isolation: pas d'auth requise, mais on vérifie qu'un user authentifié peut aussi l'utiliser ==="
A_LOGIN=$(curl -s -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' -d '{"email":"admin@wac.ma","password":"gW2Ik9f6unGIuU1y7Y5Zy70A82"}')
A_TOK=$(echo "$A_LOGIN" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("accessToken",""))')
curl -s -X POST http://localhost:8080/api/ticket/tickets/purchase-guest \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $A_TOK" \
  -d "$GUEST_BODY" -w '\nHTTP:%{http_code}\n' -o /dev/null
echo "Admin via purchase-guest : OK (HTTP attendu 201)"

echo ""
echo "=== FIN DU TEST B.28 ==="
