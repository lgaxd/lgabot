-- V4: Fila Transacional de Postagens (State Machine) e Lock Otimista

CREATE TABLE publisher_queue (
    id BIGSERIAL PRIMARY KEY,
    chat_id BIGINT NOT NULL REFERENCES bot_groups(chat_id) ON DELETE CASCADE,
    admin_id BIGINT NOT NULL,
    media_url VARCHAR(2048),
    caption TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, APPROVED, REJECTED, PUBLISHED, FAILED
    scheduled_time TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Índices vitais para acelerar o Polling do agendador (Hot-Path)
CREATE INDEX idx_publisher_queue_status_time 
ON publisher_queue (status, scheduled_time ASC);