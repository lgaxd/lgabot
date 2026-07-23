package br.com.cookiebot.moderation;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

// O uso de Record permite injeção nativa via construtor com zero boilerplate
@Service
public record BlacklistService(JdbcClient jdbcClient) {

    public boolean isGlobalBanned(Long userId) {
        // Query paramétrica limpa, retornando apenas '1' para checagem booleana super rápida
        return jdbcClient.sql("SELECT 1 FROM global_blacklist WHERE user_id = :userId")
                .param("userId", userId)
                .query(Integer.class)
                .optional()
                .isPresent();
    }
}