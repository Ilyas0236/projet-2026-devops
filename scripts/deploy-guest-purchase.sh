#!/bin/bash
# Déploiement B.28 — Achat VISITEUR sans compte
set -e
cd /home/azureuser/wydad-digital-parent

echo "=== Git pull ==="
git stash --include-untracked 2>/dev/null || true
git pull 2>&1 | tail -5
git stash pop 2>/dev/null || true

echo "=== Build auth-service ==="
cd auth-service
mvn -q clean package -DskipTests 2>&1 | tail -3

echo "=== Build ticket-service ==="
cd ../ticket-service
mvn -q clean package -DskipTests 2>&1 | tail -3

echo "=== Build api-gateway ==="
cd ../api-gateway
mvn -q clean package -DskipTests 2>&1 | tail -3

cd ..

echo "=== docker-compose build --no-cache ==="
docker compose build --no-cache auth-service ticket-service api-gateway 2>&1 | tail -10

echo "=== Restart des services ==="
docker compose up -d --no-deps auth-service ticket-service api-gateway 2>&1 | tail -10

echo "=== Attente 30s pour démarrage ==="
sleep 30

echo "=== Healthcheck ==="
curl -s -o /dev/null -w "gateway: %{http_code}\n" http://localhost:8080/api/ticket/events
curl -s -o /dev/null -w "auth: %{http_code}\n" http://localhost:8080/actuator/health
