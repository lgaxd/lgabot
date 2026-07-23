package br.com.cookiebot.moderation;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class RaidDetector {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> raidScript;

    public RaidDetector(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        
        // Script Lua atômico para controle temporal (Sliding Window Log)
        String lua = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            local clearBefore = now - window
            
            redis.call('ZREMRANGEBYSCORE', key, 0, clearBefore)
            local currentRequests = redis.call('ZCARD', key)
            
            if currentRequests < limit then
                redis.call('ZADD', key, now, now)
                redis.call('EXPIRE', key, window)
                return 1
            else
                return 0
            end
            """;
            
        this.raidScript = new DefaultRedisScript<>(lua, Long.class);
    }

    public boolean isRaid(Long chatId, long windowSeconds, int limit) {
        String key = "cookiebot:raid:" + chatId;
        long now = Instant.now().getEpochSecond();
        
        Long result = redisTemplate.execute(
                raidScript, 
                List.of(key), 
                String.valueOf(now), 
                String.valueOf(windowSeconds), 
                String.valueOf(limit)
        );
        
        // Retorna true (Raid detectado) se o script retornar 0
        return result != null && result == 0L;
    }
}