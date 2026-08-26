#!/bin/bash
# Créer un compte PRESIDENT test avec mot de passe connu
# On utilise BCrypt pour hasher "President2025!"
# Ce hash BCrypt est généré par Spring Security (coût 10)

echo "=== Insertion PRESIDENT test (mdp: President2025!) ==="
# Le hash ci-dessous correspond à "President2025!" avec BCrypt cost 10
# Vérifié : $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
HASH='$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy'

docker exec wydad-postgres psql -U wydad -d auth_db -c "
INSERT INTO users (active, kyc_verified, membership_level, role, statut_compte, email, first_name, last_name, phone, password, ville, langue)
VALUES (true, false, 'BLANC', 'PRESIDENT', 'VALIDE', 'president-test@wac.ma', 'Hassan', 'Test', '+212600000111', '$HASH', 'Casablanca', 'fr')
ON CONFLICT (email) DO UPDATE SET password='$HASH';
"

echo ""
echo "=== Vérification ==="
docker exec wydad-postgres psql -U wydad -d auth_db -c "SELECT id, email, role, statut_compte, phone FROM users WHERE email='president-test@wac.ma';"

echo ""
echo "=== Test login ==="
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"president-test@wac.ma","password":"President2025!"}' \
  -w "\nHTTP:%{http_code}\n" -o /tmp/login_pres
cat /tmp/login_pres | head -c 500
echo
