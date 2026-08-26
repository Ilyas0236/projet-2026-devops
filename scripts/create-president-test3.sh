#!/bin/bash
# On récupère le HASH bcrypt d'un user dont on CONNAÎT le mdp : admin@wac.ma
# Le hash BCrypt de l'admin est $2b$12$qGOwLjm/flrrXwlCFS3l2.EJovmR2gFKWNEQHmfkCW.FVpIwo.Aum (coût 12, $2b$)
# On l'utilise pour notre PRESIDENT test (coût 12 accepté par Spring BCryptPasswordEncoder)

ADMIN_HASH='$2b$12$qGOwLjm/flrrXwlCFS3l2.EJovmR2gFKWNEQHmfkCW.FVpIwo.Aum'

echo "=== Update PRESIDENT avec le hash de l'admin (mdp = Admin2025!) ==="
docker exec wydad-postgres psql -U wydad -d auth_db -c "
UPDATE users SET password='$ADMIN_HASH' WHERE email='president-test@wac.ma';
SELECT id, email, role, statut_compte, membership_level, phone, password FROM users WHERE email='president-test@wac.ma';
"

echo ""
echo "=== Test login avec mdp 'Admin2025!' ==="
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"president-test@wac.ma","password":"Admin2025!"}' \
  -w "\nHTTP:%{http_code}\n" -o /tmp/login_pres
echo "--- Réponse ---"
cat /tmp/login_pres | head -c 1000
echo
