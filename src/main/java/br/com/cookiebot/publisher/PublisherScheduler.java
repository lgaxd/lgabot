package br.com.cookiebot.publisher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@EnableScheduling
public class PublisherScheduler {

    private static final Logger log = LoggerFactory.getLogger(PublisherScheduler.class);
    private final JdbcClient jdbcClient;
    private final ExecutorService virtualThreadExecutor;

    public PublisherScheduler(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    // Record para a projeção rápida do banco
    private record PostTask(Long id, Long chatId, String mediaUrl, String caption) {}

    // Polling a cada 30 segundos conforme especificação
    @Scheduled(fixedRate = 30000)
    public void processQueue() {
        // FOR UPDATE SKIP LOCKED garante exclusividade atômica (sem travar a tabela toda)
        List<PostTask> pendingTasks = jdbcClient.sql("""
                SELECT id, chat_id, media_url, caption 
                FROM publisher_queue 
                WHERE status = 'APPROVED' AND scheduled_time <= CURRENT_TIMESTAMP 
                ORDER BY scheduled_time ASC 
                FOR UPDATE SKIP LOCKED LIMIT 20
                """)
                .query(PostTask.class)
                .list();

        if (pendingTasks.isEmpty()) {
            return;
        }

        log.info("Encontrados {} posts aprovados na fila. Iniciando disparos...", pendingTasks.size());

        for (PostTask task : pendingTasks) {
            // Delega o I/O bloqueante do envio ao Telegram para uma Virtual Thread
            virtualThreadExecutor.submit(() -> publishToTelegram(task));
        }
    }

    private void publishToTelegram(PostTask task) {
        try {
            // Placeholder: Aqui será a chamada HTTP POST para a API de envio do Telegram.
            // ...
            
            // Sucesso: Atualiza status para FINALIZADO
            markAsFinished(task.id(), "PUBLISHED");
            log.info("Post ID {} publicado com sucesso no chat {}.", task.id(), task.chatId());
            
        } catch (Exception e) {
            log.error("Erro ao publicar o post ID {}: {}", task.id(), e.getMessage());
            markAsFinished(task.id(), "FAILED");
        }
    }

    private void markAsFinished(Long postId, String finalStatus) {
        jdbcClient.sql("UPDATE publisher_queue SET status = :status WHERE id = :id")
                .param("status", finalStatus)
                .param("id", postId)
                .update();
    }
}