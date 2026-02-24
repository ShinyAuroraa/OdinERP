-- V10__create_audit_logs.sql
-- Registro imutável de todas as ações administrativas (alterações de role, ativação/inativação).
-- Retenção mínima 12 meses conforme LGPD (NFR10).
-- Particionamento por occurred_at pode ser adicionado via V10b quando política de
-- gerenciamento de partições estiver estabelecida pela equipe de operações.

CREATE TABLE audit_logs (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    entity_type VARCHAR(50)  NOT NULL,
    entity_id   UUID         NOT NULL,
    action      VARCHAR(50)  NOT NULL,
    actor_id    UUID         NOT NULL,
    actor_email VARCHAR(100),
    old_values  JSONB,
    new_values  JSONB,
    ip_address  INET,
    user_agent  VARCHAR(500),
    occurred_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_audit_logs PRIMARY KEY (id)
);

-- Índice para consultas por entidade (ex: histórico de um usuário)
CREATE INDEX idx_audit_entity ON audit_logs (entity_type, entity_id, occurred_at DESC);
