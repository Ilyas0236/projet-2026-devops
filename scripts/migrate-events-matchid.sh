#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# V1.1 — Migration ticket-service : ajout de la colonne events.match_id (FK
# logique vers content-service.matches.id).
#
# Le service ticket-service tourne dans le conteneur "ticket-service" et
# utilise la base "ticket_db" (cf. docker-compose.yml). Le contenu de la
# table matches, lui, vit dans content_db. On NE crée PAS de contrainte FK
# physique cross-base : c'est intentionnel (les services sont indépendants).
# La cohérence est gérée côté UI (sélecteur fermé sur getMatches()).
#
# Idempotente : ALTER ... IF NOT EXISTS (Postgres 9.6+).
# -----------------------------------------------------------------------------
set -euo pipefail

CONTAINER="${TICKET_DB_CONTAINER:-ticket-db}"
DB="${TICKET_DB_NAME:-ticket_db}"
USER="${TICKET_DB_USER:-wydad}"

echo "[migrate-events-matchid] conteneur=$CONTAINER db=$DB"

docker exec -i "$CONTAINER" psql -U "$USER" -d "$DB" <<'SQL'
-- V1.1 — ajout du lien optionnel vers le match de calendrier (content-service).
ALTER TABLE events
  ADD COLUMN IF NOT EXISTS match_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_events_match_id ON events(match_id);

-- Pas de FK : match_id pointe vers content-service.matches (autre base).
-- La cohérence est vérifiée à la saisie via le sélecteur front (getMatches).
SQL

echo "[migrate-events-matchid] OK"
