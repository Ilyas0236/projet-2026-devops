#!/bin/bash
# Vérifier les comptes PRESIDENT en base + leurs mots de passe
echo "=== Comptes PRESIDENT ==="
docker exec auth-postgres psql -U wydad_auth -d auth_db -c "SELECT id, email, role, status, phone, last_name, first_name FROM users WHERE role='PRESIDENT' ORDER BY id;"

echo ""
echo "=== Recherche compte avec rôle président connu (id=114) ==="
docker exec auth-postgres psql -U wydad_auth -d auth_db -c "SELECT id, email, role, status, phone, password_hash FROM users WHERE id=114;"

echo ""
echo "=== Test login avec mdp commun ==="
echo "Test 1: admin2024"
curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"email":"president@wac.ma","password":"Admin2024!"}' -w "\nHTTP:%{http_code}\n" -o /tmp/r1
cat /tmp/r1 | head -c 300; echo
