-- V1__create_schema.sql
-- Instala extensões necessárias para o schema do ODIN CRM
-- pgcrypto : fornece gen_random_uuid() para PKs UUID em todas as tabelas
-- pg_trgm  : permite índices GIN trigram para busca textual (razao_social, nome_fantasia)

CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- Função de trigger reutilizada por todas as tabelas com coluna updated_at.
-- Acionada automaticamente BEFORE UPDATE para manter updated_at sincronizado.
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
