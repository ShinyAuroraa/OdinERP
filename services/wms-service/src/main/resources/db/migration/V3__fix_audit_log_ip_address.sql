-- Story 1.2: Fix ip_address column type inet → text
-- PostgreSQL inet type causes JDBC type mismatch when binding String values.
-- JPA/Hibernate sends character varying, but inet requires explicit PGobject handling.
-- text is sufficient for storing IPv4/IPv6 addresses (max 45 chars) without DB-level validation.
ALTER TABLE audit_log ALTER COLUMN ip_address TYPE TEXT USING ip_address::TEXT;
