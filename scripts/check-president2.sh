#!/bin/bash
# Vérifier les comptes PRESIDENT en base
echo "=== Comptes PRESIDENT ==="
docker exec wydad-postgres psql -U wydad -d auth_db -c "SELECT id, email, role, status, phone FROM users WHERE role='PRESIDENT' ORDER BY id;"

echo ""
echo "=== Compte id=114 ==="
docker exec wydad-postgres psql -U wydad -d auth_db -c "SELECT id, email, role, status, phone FROM users WHERE id=114;"

echo ""
echo "=== Toutes les bases ==="
docker exec wydad-postgres psql -U wydad -c "SELECT datname FROM pg_database WHERE datistemplate=false;"
