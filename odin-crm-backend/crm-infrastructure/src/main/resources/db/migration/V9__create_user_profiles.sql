-- V9__create_user_profiles.sql
-- Perfis de usuário sincronizados do Keycloak.
-- keycloak_id: UUID do usuário no Keycloak (fonte de verdade para auth/roles).
-- email e telefone criptografados em AES-256 (LGPD — NFR5).
-- email_hash SHA-256 para lookup sem descriptografar.

CREATE TABLE user_profiles (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    keycloak_id     VARCHAR(36)  NOT NULL,
    nome            VARCHAR(255) NOT NULL,
    email           VARCHAR(500) NOT NULL,   -- AES-256 encrypted
    email_hash      VARCHAR(64)  NOT NULL,   -- SHA-256 for lookup
    cargo           VARCHAR(100),
    telefone        VARCHAR(100),            -- AES-256 encrypted
    preferencias    JSONB        NOT NULL DEFAULT '{}',
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_user_profiles  PRIMARY KEY (id),
    CONSTRAINT uq_user_keycloak  UNIQUE (keycloak_id),
    CONSTRAINT uq_user_email_hash UNIQUE (email_hash)
);

CREATE TRIGGER trg_user_profiles_updated_at
    BEFORE UPDATE ON user_profiles
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
