package br.com.cookiebot.cache;

import br.com.cookiebot.cache.domain.ChatConfig;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class ChatConfigCacheService {

    private static final Logger log = LoggerFactory.getLogger(ChatConfigCacheService.class);
    private final Cache<Long, ChatConfig> l1Cache;

    public ChatConfigCacheService() {
        // Capacidade máxima de 5.000 entradas e TTL de 5 minutos na memória RAM
        this.l1Cache = Caffeine.newBuilder()
                .maximumSize(5000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();
    }

    public ChatConfig getConfig(Long chatId) {
        // Tenta buscar no L1 (latência < 0.5ms)
        ChatConfig config = l1Cache.getIfPresent(chatId);
        
        if (config != null) {
            return config;
        }

        // Simulação de busca no banco e fallback (que será acoplada quando o repositório for implementado)
        log.info("Cache Miss (L1) para o chat {}. O fallback consultará o banco transacional.", chatId);
        
        return null;
    }

    public void evictLocal(Long chatId) {
        l1Cache.invalidate(chatId);
    }
}