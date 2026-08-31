-- ============================================================================
-- Migration B.18 — Achat PARENT pour un enfant académie (subscription)
-- ============================================================================
-- À exécuter manuellement en prod après `mvn clean package` du auth-service.
-- Idempotente : IF NOT EXISTS (PostgreSQL 9.6+).
--
-- Contexte : un parent (rôle PARENT) peut désormais acheter un abonnement
-- saisonnier au nom de son enfant académie (sports-service.AcademyMember).
-- L'abonnement reste rattaché à l'User shadow de l'enfant (créé via
-- ChildUserService) mais on garde la traçabilité du parent payeur et
-- de l'academyMemberId pour les listings admin et les remboursements.
-- ============================================================================

ALTER TABLE user_subscriptions
    ADD COLUMN IF NOT EXISTS beneficiary_academy_member_id BIGINT;

ALTER TABLE user_subscriptions
    ADD COLUMN IF NOT EXISTS parent_payer_email VARCHAR(256);

CREATE INDEX IF NOT EXISTS idx_user_subscription_beneficiary
    ON user_subscriptions (beneficiary_academy_member_id);
