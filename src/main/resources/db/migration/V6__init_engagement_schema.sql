-- V6: Aniversários e Sorteios (Giveaways) com Lock Otimista

CREATE TABLE user_birthdays (
    user_id BIGINT PRIMARY KEY REFERENCES bot_users(id) ON DELETE CASCADE,
    birth_date DATE NOT NULL,
    avatar_url VARCHAR(2048),
    notified_year INT
);

CREATE TABLE giveaways (
    id BIGSERIAL PRIMARY KEY,
    chat_id BIGINT NOT NULL REFERENCES bot_groups(chat_id) ON DELETE CASCADE,
    item_name VARCHAR(255) NOT NULL,
    max_winners INT NOT NULL DEFAULT 1,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0, -- Lock Otimista (@Version)
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE giveaway_participants (
    giveaway_id BIGINT NOT NULL REFERENCES giveaways(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL,
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (giveaway_id, user_id)
);