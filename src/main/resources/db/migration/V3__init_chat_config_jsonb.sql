-- V3: Configurações de Grupo e coluna JSONB nativa

CREATE TABLE chat_configs (
    chat_id BIGINT PRIMARY KEY REFERENCES bot_groups(chat_id) ON DELETE CASCADE,
    welcome_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    anti_raid_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    raid_sensitivity INT NOT NULL DEFAULT 5,
    custom_rules_matrix JSONB,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);