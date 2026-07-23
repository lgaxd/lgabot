-- V2: Tabelas de Moderação e Blacklist Global
-- Operações nestas tabelas ocorrerão estritamente via JdbcClient (Hot-Path)

CREATE TABLE global_blacklist (
    user_id BIGINT NOT NULL PRIMARY KEY,
    reason VARCHAR(255),
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);