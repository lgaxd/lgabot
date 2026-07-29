package br.com.cookiebot.publisher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class PublisherModerationService {

    private static final Logger log = LoggerFactory.getLogger(PublisherModerationService.class);
    private final JdbcClient jdbcClient;

    public PublisherModerationService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public boolean approvePost(Long postId, Long currentVersion) {
        return changeStatus(postId, currentVersion, "APPROVED");
    }

    public boolean rejectPost(Long postId, Long currentVersion) {
        return changeStatus(postId, currentVersion, "REJECTED");
    }

    private boolean changeStatus(Long postId, Long currentVersion, String newStatus) {
        // Atualização atômica rigorosa via controle otimista de concorrência (@Version manual)
        int updatedRows = jdbcClient.sql("""
                UPDATE publisher_queue 
                SET status = :status, version = version + 1 
                WHERE id = :id AND version = :version
                """)
                .param("status", newStatus)
                .param("id", postId)
                .param("version", currentVersion)
                .update();
                
        if (updatedRows == 0) {
            log.warn("Falha na moderação do Post ID {}. Concorrência detectada ou post não existe.", postId);
            return false;
        }
        
        log.info("Post ID {} atualizado para {}.", postId, newStatus);
        return true;
    }
}