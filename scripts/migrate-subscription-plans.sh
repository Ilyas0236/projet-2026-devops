#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# Lot 2 (B.12) — Migration auth-service : entite SubscriptionPlan editable.
#
# Le service auth-service tourne dans le conteneur "auth-service" et utilise
# la base "auth_db" (cf. docker-compose.yml). Avant la migration, la grille
# d'abonnement etait codee en dur dans l'enum SubscriptionZoneCode. Apres,
# elle est portee par la table subscription_plans (editable par l'admin)
# et l'enum reste comme colonne legacy sur user_subscriptions.zone_code.
#
# Etapes :
#  1. Arret auth-service (necessaire : ddl-auto=update ne sait pas creer
#     la table + backfill dans la meme transaction).
#  2. CREATE TABLE subscription_plans + index.
#  3. ALTER TABLE user_subscriptions ADD COLUMN plan_id + FK ON DELETE SET
#     NULL + index.
#  4. Seed initial via le seeder Java (au prochain demarrage du service) :
#     aligne sur l'enum, idempotent (ne reecrase pas les prix edites par
#     l'admin).
#  5. Backfill user_subscriptions.plan_id depuis zone_code (chaque
#     SubscriptionZoneCode a un plan cree par le seeder, on remplit donc
#     directement par le code de zone).
#
# Idempotente : IF NOT EXISTS partout, ON CONFLICT DO NOTHING sur le seed.
# -----------------------------------------------------------------------------
set -euo pipefail

CONTAINER="${AUTH_DB_CONTAINER:-auth-db}"
DB="${AUTH_DB_NAME:-auth_db}"
USER="${AUTH_DB_USER:-wydad}"

echo "[migrate-subscription-plans] conteneur=$CONTAINER db=$DB"

docker exec -i "$CONTAINER" psql -U "$USER" -d "$DB" <<'SQL'
-- Lot 2 / B.12 — table des plans d'abonnement (editable admin).
CREATE TABLE IF NOT EXISTS subscription_plans (
  id BIGSERIAL PRIMARY KEY,
  code VARCHAR(32) NOT NULL UNIQUE,
  name VARCHAR(128) NOT NULL,
  regular_price NUMERIC(10,2) NOT NULL DEFAULT 0,
  adherent_price NUMERIC(10,2) NOT NULL DEFAULT 0,
  benefits TEXT,
  is_active BOOLEAN NOT NULL DEFAULT true,
  display_order INT NOT NULL DEFAULT 0,
  exceptional_priority BOOLEAN NOT NULL DEFAULT false,
  season VARCHAR(16),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_sub_plan_active_order
  ON subscription_plans(is_active, display_order);

-- Lot 2 / B.12 — FK nullable sur user_subscriptions, ON DELETE SET NULL
-- pour ne pas casser l'historique si un plan est supprime.
ALTER TABLE user_subscriptions
  ADD COLUMN IF NOT EXISTS plan_id BIGINT;
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
     WHERE conname = 'fk_user_subscriptions_plan'
  ) THEN
    ALTER TABLE user_subscriptions
      ADD CONSTRAINT fk_user_subscriptions_plan
      FOREIGN KEY (plan_id) REFERENCES subscription_plans(id) ON DELETE SET NULL;
  END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_user_subscription_plan
  ON user_subscriptions(plan_id);
SQL

echo "[migrate-subscription-plans] schema OK"
echo "[migrate-subscription-plans] NOTE : le backfill plan_id et le seed"
echo "  initial sont executes automatiquement par SubscriptionPlanSeeder"
echo "  (ApplicationReadyEvent, idempotent) au prochain demarrage du"
echo "  service auth. Ne PAS le faire ici en SQL : on laisse le seeder"
echo "  Java inserer les plans (source de verite = enum + prix)"
echo "  puis on remplit plan_id par jointure sur code."

echo "[migrate-subscription-plans] OK"
