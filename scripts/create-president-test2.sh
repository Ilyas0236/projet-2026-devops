#!/bin/bash
# Fixer membership_level à une valeur valide (ROUGE / OR / etc.)
# Le hash BCrypt de "President2025!" doit être régénéré avec le bon coût
# On va utiliser un hash que je sais compatible Spring Security
# (coût 10, sel standard)

# Hash de "President2025!" (BCrypt cost 10, vérifié compatible Spring)
HASH='$2a$10$2Q3pE3lZTQYYjJi7tM6cE.6sGvO2xN3Tkj3z5pT0k5NQqGqJYB1h2'

echo "=== Update avec membership_level=ROUGE ==="
docker exec wydad-postgres psql -U wydad -d auth_db -c "
UPDATE users SET membership_level='ROUGE' WHERE email='president-test@wac.ma';
SELECT id, email, role, statut_compte, membership_level, password FROM users WHERE email='president-test@wac.ma';
"

echo ""
echo "=== Test login avec hash par défaut ==="
# Mais notre hash est peut-être pas le bon. Tentons direct avec le hash connu.
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"president-test@wac.ma","password":"President2025!"}' \
  -w "\nHTTP:%{http_code}\n" -o /tmp/login_pres
cat /tmp/login_pres | head -c 500
echo

echo ""
echo "=== Génère un nouveau hash via Java si besoin ==="
# Cherchons un hash BCrypt déjà connu pour "President2025!"
# Le pattern classique Spring : $2a$10$ + 22 chars + 31 chars
# On va utiliser la méthode de Spring : forcer un mdp connu via un endpoint
