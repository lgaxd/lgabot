-- V5: Cache Relacional para Argos Translate

CREATE TABLE translation_cache (
    hash_key VARCHAR(64) PRIMARY KEY,
    source_lang VARCHAR(10) NOT NULL,
    target_lang VARCHAR(10) NOT NULL,
    original_text TEXT NOT NULL,
    translated_text TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Índice para acelerar a busca de traduções frequentes
CREATE INDEX idx_translation_cache_langs ON translation_cache(source_lang, target_lang);