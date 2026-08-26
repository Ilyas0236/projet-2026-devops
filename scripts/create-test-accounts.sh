#!/bin/bash
# Crée PARENT + PRESIDENT en se servant de l'app pour hasher le password (BCrypt correct).
# - 2 comptes jetés (mot de passe Audit2026!) hashés par l'app
# - UPDATE role en SQL vers PARENT/PRESIDENT
set -e

BASE=http://localhost:8080
TS=$(date +%s)
echo "TS=$TS"

# 1) Créer 2 comptes en mode ADHERENT (sans demandeRole → VALIDE direct)
EMAIL_PARENT="audit-parent-$TS@wac.ma"
EMAIL_PRESIDENT="audit-president-$TS@wac.ma"
PHONE_PARENT="+2126001${TS:5:6}"
PHONE_PRESIDENT="+2126002${TS:5:6}"

echo "=== Création PARENT ($EMAIL_PARENT) ==="
curl -s -X POST "$BASE/api/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL_PARENT\",\"phone\":\"$PHONE_PARENT\",\"password\":\"Audit2026!\",\"firstName\":\"Parent\",\"lastName\":\"Test\"}" \
  -w "\nHTTP=%{http_code}\n"

echo "=== Création PRESIDENT ($EMAIL_PRESIDENT) ==="
curl -s -X POST "$BASE/api/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL_PRESIDENT\",\"phone\":\"$PHONE_PRESIDENT\",\"password\":\"Audit2026!\",\"firstName\":\"President\",\"lastName\":\"Test\"}" \
  -w "\nHTTP=%{http_code}\n"

# 2) Vérifier le hash BCrypt généré par l'app (pour PRESIDENT)
BCRYPT=$(docker exec wydad-postgres psql -U wydad -d auth_db -tA -c "SELECT password FROM users WHERE email='$EMAIL_PRESIDENT';")
echo "BCrypt hash from app: $BCRYPT"

# 3) UPDATE role en SQL
docker exec wydad-postgres psql -U wydad -d auth_db <<SQL
UPDATE users SET role='PARENT' WHERE email='$EMAIL_PARENT';
UPDATE users SET role='PRESIDENT' WHERE email='$EMAIL_PRESIDENT';
SQL

# 4) Récupérer IDs
PARENT_ID=$(docker exec wydad-postgres psql -U wydad -d auth_db -tA -c "SELECT id FROM users WHERE email='$EMAIL_PARENT';")
PRESIDENT_ID=$(docker exec wydad-postgres psql -U wydad -d auth_db -tA -c "SELECT id FROM users WHERE email='$EMAIL_PRESIDENT';")

# 5) Login pour récupérer les tokens
PTOK_PARENT=$(curl -s -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL_PARENT\",\"password\":\"Audit2026!\"}" | jq -r '.accessToken // "NO_TOKEN")
')
PTOK_PRESIDENT=$(curl -s -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL_PRESIDENT\",\"password\":\"Audit2026!\"}" | jq -r '.accessToken // "NO_TOKEN")
')

cat > /tmp/audit-tokens-extended.sh <<EOF
export PARENT_ID=$PARENT_ID
export PRESIDENT_ID=$PRESIDENT_ID
export EMAIL_PARENT=$EMAIL_PARENT
export EMAIL_PRESIDENT=$EMAIL_PRESIDENT
export PTOK_PARENT='$PTOK_PARENT'
export PTOK_PRESIDENT='$PTOK_PRESIDENT'
EOF

echo ""
echo "============================================="
echo "  PARENT_ID=$PARENT_ID"
echo "  PRESIDENT_ID=$PRESIDENT_ID"
echo "  EMAIL_PARENT=$EMAIL_PARENT"
echo "  EMAIL_PRESIDENT=$EMAIL_PRESIDENT"
echo "  Tokens dans /tmp/audit-tokens-extended.sh"
echo "============================================="
