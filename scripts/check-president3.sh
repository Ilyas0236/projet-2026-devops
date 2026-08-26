#!/bin/bash
echo "=== Bases ==="
docker exec wydad-postgres psql -U wydad -c "SELECT datname FROM pg_database WHERE datistemplate=false;"

echo ""
echo "=== Colonnes users ==="
docker exec wydad-postgres psql -U wydad -d auth_db -c "\d users" 2>&1 | head -30

echo ""
echo "=== Comptes PRESIDENT (sans status) ==="
docker exec wydad-postgres psql -U wydad -d auth_db -c "SELECT id, email, role, phone FROM users WHERE role='PRESIDENT' ORDER BY id;"

echo ""
echo "=== 3 premiers users (colonnes) ==="
docker exec wydad-postgres psql -U wydad -d auth_db -c "SELECT * FROM users ORDER BY id LIMIT 3;"
