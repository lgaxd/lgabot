package br.com.cookiebot.moderation;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class StickerAntiSpam {

    private final StringRedisTemplate redisTemplate;

    public StickerAntiSpam(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isSpamming(Long chatId, Long userId) {
        String key = "cookiebot:spam:media:" + chatId + ":" + userId;
        
        Long count = redisTemplate.opsForValue().increment(key);
        
        if (count != null && count == 1L) {
            // Expira a chave após 10 segundos
            redisTemplate.expire(key, Duration.ofSeconds(10));
        }
        
        // Retorna true se o usuário enviou mais de 5 mídias/stickers em 10 segundos
        return count != null && count > 5;
    }
}