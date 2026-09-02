-- V1 — Sélection explicite des joueurs convoqués à une séance.
-- Une ligne par couple (séance, joueur). Choix nominatif de l'entraîneur,
-- notification in-app personnalisée (status DRAFT → CONVOQUE), lecture ADMIN.
-- Modèle Java : com.wydad.digital.sports.model.SessionConvocation

CREATE TABLE session_convocations (
    id                          BIGSERIAL    PRIMARY KEY,
    session_id                  BIGINT       NOT NULL,
    sport_type                  VARCHAR(32)  NOT NULL,
    category                    VARCHAR(32)  NOT NULL,
    joueur_user_id              BIGINT       NOT NULL,
    status                      VARCHAR(16)  NOT NULL,
    created_by_staff_user_id    BIGINT       NOT NULL,
    created_at                  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMP    NOT NULL DEFAULT now(),

    -- Un joueur ne peut être convoqué qu'une fois par séance.
    CONSTRAINT uk_session_convocation_player
        UNIQUE (session_id, joueur_user_id),

    -- Cohérence interne : status n'accepte que les valeurs de l'enum Java.
    CONSTRAINT ck_session_convocation_status
        CHECK (status IN ('DRAFT', 'CONVOQUE'))
);

-- Lookups par séance (vue ADMIN : joueurs d'une séance).
CREATE INDEX idx_session_convocation_session_id
    ON session_convocations (session_id);

-- Lookups par joueur (vue JOUEUR : /sessions/my).
CREATE INDEX idx_session_convocation_joueur_user_id
    ON session_convocations (joueur_user_id);
