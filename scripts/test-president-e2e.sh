#!/bin/bash
# Test E2E complet : WORKFLOW DOCUMENTS PRÉSIDENT → ADMIN
# Utilise president-test@wac.ma (id=119) avec mdp President2025!

set +e
P_EMAIL="president-test@wac.ma"
P_PWD="President2025!"

# 0. Régénère un hash frais (BCrypt = sel aléatoire)
HASH=$(java -cp /tmp/bcrypt:/home/azureuser/.m2/repository/org/springframework/security/spring-security-crypto/6.3.4/spring-security-crypto-6.3.4.jar:/home/azureuser/.m2/repository/commons-logging/commons-logging/1.2/commons-logging-1.2.jar Hash "$P_PWD" "X" 2>&1 | grep FRESH | sed 's/FRESH://')
echo "Hash généré: $HASH"

# 1. Mettre à jour le hash du PRESIDENT test
echo "=== 1. Update hash du PRESIDENT test ==="
docker exec wydad-postgres psql -U wydad -d auth_db -c "UPDATE users SET password='$HASH' WHERE email='$P_EMAIL';"

# 2. Login PRESIDENT
echo ""
echo "=== 2. Login PRESIDENT ==="
P_LOGIN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$P_EMAIL\",\"password\":\"$P_PWD\"}")
echo "$P_LOGIN" | head -c 300
P_TOK=$(echo "$P_LOGIN" | python3 -c "import sys,json; print(json.load(sys.stdin).get('accessToken',''))" 2>/dev/null)
P_ID=$(echo "$P_LOGIN" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null)
echo ""
echo "P_TOK len=${#P_TOK}, P_ID=$P_ID"

if [ ${#P_TOK} -lt 20 ]; then
  echo "ÉCHEC login PRESIDENT. Abandon."
  exit 1
fi

# 3. Login ADMIN
echo ""
echo "=== 3. Login ADMIN ==="
A_LOGIN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@wac.ma","password":"gW2Ik9f6unGIuU1y7Y5Zy70A82"}')
echo "$A_LOGIN" | head -c 200
echo ""
A_TOK=$(echo "$A_LOGIN" | python3 -c "import sys,json; print(json.load(sys.stdin).get('accessToken',''))" 2>/dev/null)
A_ID=$(echo "$A_LOGIN" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null)
echo "A_TOK len=${#A_TOK}, A_ID=$A_ID"

if [ ${#A_TOK} -lt 20 ]; then
  echo "ÉCHEC login ADMIN. Abandon."
  exit 1
fi

# 4. PRÉSIDENT crée un brouillon
echo ""
echo "=== 4. PRESIDENT crée un brouillon ==="
CREATE=$(curl -s -X POST http://localhost:8080/api/content/president-documents \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $P_TOK" \
  -H "X-User-Id: $P_ID" \
  -H "X-User-Email: $P_EMAIL" \
  -d '{"category":"COMMUNIQUE","title":"Rapport moral 2025-2026","content":"Le WAC continue sa progression..."}')
echo "$CREATE" | head -c 500
DOC_ID=$(echo "$CREATE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null)
echo ""
echo "DOC_ID=$DOC_ID"

if [ -z "$DOC_ID" ]; then
  echo "ÉCHEC création brouillon. Abandon."
  exit 1
fi

# 5. PRÉSIDENT soumet le brouillon
echo ""
echo "=== 5. PRESIDENT soumet (DRAFT → SUBMITTED) ==="
curl -s -X POST "http://localhost:8080/api/content/president-documents/$DOC_ID/submit" \
  -H "Authorization: Bearer $P_TOK" \
  -H "X-User-Id: $P_ID" \
  -H "X-User-Email: $P_EMAIL" \
  -w "\nHTTP:%{http_code}\n" | head -c 400
echo ""

# 6. ADMIN voit la file d'attente
echo ""
echo "=== 6. ADMIN /admin/pending ==="
curl -s "http://localhost:8080/api/content/president-documents/admin/pending" \
  -H "Authorization: Bearer $A_TOK" \
  -H "X-User-Id: $A_ID" \
  -H "X-User-Email: admin@wac.ma" \
  -w "\nHTTP:%{http_code}\n" | head -c 600
echo ""

# 7. ADMIN approuve
echo ""
echo "=== 7. ADMIN approuve (SUBMITTED → APPROVED) ==="
curl -s -X POST "http://localhost:8080/api/content/president-documents/admin/$DOC_ID/approve" \
  -H "Authorization: Bearer $A_TOK" \
  -H "X-User-Id: $A_ID" \
  -H "X-User-Email: admin@wac.ma" \
  -w "\nHTTP:%{http_code}\n" | head -c 400
echo ""

# 8. ADMIN publie
echo ""
echo "=== 8. ADMIN publie (APPROVED → PUBLISHED) ==="
curl -s -X POST "http://localhost:8080/api/content/president-documents/admin/$DOC_ID/publish" \
  -H "Authorization: Bearer $A_TOK" \
  -H "X-User-Id: $A_ID" \
  -H "X-User-Email: admin@wac.ma" \
  -w "\nHTTP:%{http_code}\n" | head -c 400
echo ""

# 9. Membre authentifié voit le document publié
echo ""
echo "=== 9. ADHERENT voit /published ==="
M_LOGIN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"dbg.j.1787714479@test.wac.ma","password":"x"}' 2>/dev/null)
# Cet ADHERENT test n'a pas de mdp connu, on tente l'ID 20
curl -s "http://localhost:8080/api/content/president-documents/published" \
  -H "X-User-Id: 20" \
  -w "\nHTTP:%{http_code}\n" | head -c 600
echo ""

echo ""
echo "=== 10. Test REJECTED ==="
# Création d'un 2e document pour le test REJECT
CREATE2=$(curl -s -X POST http://localhost:8080/api/content/president-documents \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $P_TOK" \
  -H "X-User-Id: $P_ID" \
  -H "X-User-Email: $P_EMAIL" \
  -d '{"category":"RAPPORT_FINANCIER","title":"Budget 2026 provisoire","content":"Estimations..."}')
DOC2_ID=$(echo "$CREATE2" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null)
echo "DOC2_ID=$DOC2_ID"
# Soumettre
curl -s -X POST "http://localhost:8080/api/content/president-documents/$DOC2_ID/submit" \
  -H "Authorization: Bearer $P_TOK" \
  -H "X-User-Id: $P_ID" \
  -H "X-User-Email: $P_EMAIL" \
  -w "\nsubmit HTTP:%{http_code}\n" >/dev/null
# Refuser avec motif
curl -s -X POST "http://localhost:8080/api/content/president-documents/admin/$DOC2_ID/reject" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $A_TOK" \
  -H "X-User-Id: $A_ID" \
  -H "X-User-Email: admin@wac.ma" \
  -d '{"motif":"Données insuffisantes, merci de compléter le rapport"}' \
  -w "\nHTTP:%{http_code}\n" | head -c 400
echo ""

echo ""
echo "=== 11. Test isolation : PRESIDENT n'accède pas à /admin/pending ==="
curl -s "http://localhost:8080/api/content/president-documents/admin/pending" \
  -H "Authorization: Bearer $P_TOK" \
  -H "X-User-Id: $P_ID" \
  -H "X-User-Email: $P_EMAIL" \
  -w "\nHTTP:%{http_code}\n" -o /dev/null

echo ""
echo "=== 12. Test isolation : ADHERENT (id=20) n'accède pas à /admin/pending ==="
curl -s "http://localhost:8080/api/content/president-documents/admin/pending" \
  -H "X-User-Id: 20" \
  -H "X-User-Email: dbg.j.1787714479@test.wac.ma" \
  -w "\nHTTP:%{http_code}\n" -o /dev/null

echo ""
echo "=== 13. Test refus sans motif (doit échouer 400) ==="
CREATE3=$(curl -s -X POST http://localhost:8080/api/content/president-documents \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $P_TOK" \
  -H "X-User-Id: $P_ID" \
  -H "X-User-Email: $P_EMAIL" \
  -d '{"category":"PROJET_CLUB","title":"Projet centre formation","content":"..."}')
DOC3_ID=$(echo "$CREATE3" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null)
curl -s -X POST "http://localhost:8080/api/content/president-documents/$DOC3_ID/submit" \
  -H "Authorization: Bearer $P_TOK" \
  -H "X-User-Id: $P_ID" \
  -H "X-User-Email: $P_EMAIL" >/dev/null
curl -s -X POST "http://localhost:8080/api/content/president-documents/admin/$DOC3_ID/reject" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $A_TOK" \
  -H "X-User-Id: $A_ID" \
  -H "X-User-Email: admin@wac.ma" \
  -d '{"motif":""}' \
  -w "\nHTTP:%{http_code}\n" | head -c 200
echo ""

echo ""
echo "=== FIN DU TEST E2E ==="
