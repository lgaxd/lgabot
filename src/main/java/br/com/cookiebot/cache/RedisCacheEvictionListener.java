package br.com.cookiebot.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class RedisCacheEvictionListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheEvictionListener.class);
    private final ChatConfigCacheService cacheService;

    public RedisCacheEvictionListener(ChatConfigCacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String chatIdStr = new String(message.getBody(), StandardCharsets.UTF_8);
            Long chatId = Long.parseLong(chatIdStr);
            
            // Invalida a entrada no cache L1 local
            cacheService.evictLocal(chatId);
            log.info("Sinal Pub/Sub recebido. Cache L1 invalidado para o chat: {}", chatId);
            
        } catch (NumberFormatException e) {
            log.error("Formato inválido de ID de chat recebido no canal de invalidação.", e);
        }
    }
}