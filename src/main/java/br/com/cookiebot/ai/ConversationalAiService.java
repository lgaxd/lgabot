package br.com.cookiebot.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Service
public class ConversationalAiService {

    private static final Logger log = LoggerFactory.getLogger(ConversationalAiService.class);
    private final RestClient restClient;
    private final StringRedisTemplate redisTemplate;
    private final String llmEndpoint;

    public ConversationalAiService(
            RestClient.Builder restClientBuilder,
            StringRedisTemplate redisTemplate,
            @Value("${cookiebot.llm.url}") String llmEndpoint) {
        this.restClient = restClientBuilder.build();
        this.redisTemplate = redisTemplate;
        this.llmEndpoint = llmEndpoint;
    }

    public String generateReply(Long chatId, Long userId, String prompt) {
        String contextKey = "cookiebot:chat:context:" + chatId + ":" + userId;
        
        // Recupera o contexto anterior ou inicia vazio
        String previousContext = redisTemplate.opsForValue().get(contextKey);
        String fullPrompt = (previousContext != null ? previousContext + "\n" : "") + "User: " + prompt;

        try {
            // Chamada HTTP via Virtual Thread (não bloqueia threads do SO)
            String aiResponse = restClient.post()
                    .uri(llmEndpoint)
                    .body(new LlmRequest(fullPrompt))
                    .retrieve()
                    .body(String.class);

            // Monta o novo contexto e aplica o TTL inegociável de 5 minutos
            String newContext = fullPrompt + "\nBot: " + aiResponse;
            redisTemplate.opsForValue().set(contextKey, newContext, Duration.ofMinutes(5));

            return aiResponse;
            
        } catch (Exception e) {
            log.error("Falha ao comunicar com o LLM local", e);
            return "Desculpe, meu motor cognitivo está temporariamente indisponível.";
        }
    }

    // Record interno para encapsular o payload do LLM sem expor DTOs globais
    private record LlmRequest(String prompt) {}
}