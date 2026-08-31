-- ============================================================================
-- Migration B.18 — Achat PARENT pour un enfant académie (tickets)
-- ============================================================================
-- À exécuter manuellement en prod après `mvn clean package` du ticket-service.
-- Idempotente : IF NOT EXISTS (PostgreSQL 9.6+).
--
-- Contexte : un parent (rôle PARENT) peut désormais acheter un billet
-- de match au nom de son enfant académie (sports-service.AcademyMember).
-- Le billet reste rattaché à l'User shadow de l'enfant (créé via
-- ChildUserService dans auth-service) mais on garde la traçabilité
-- du parent payeur et de l'academyMemberId pour les listings admin,
-- les annulations et les remboursements E-Cash.
-- ============================================================================

ALTER TABLE tickets
    ADD COLUMN IF NOT EXISTS beneficiary_academy_member_id BIGINT;

ALTER TABLE tickets
    ADD COLUMN IF NOT EXISTS parent_payer_email VARCHAR(256);

CREATE INDEX IF NOT EXISTS idx_tickets_beneficiary
    ON tickets (beneficiary_academy_member_id);
