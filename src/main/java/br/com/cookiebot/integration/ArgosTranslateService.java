package br.com.cookiebot.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class ArgosTranslateService {

    private static final Logger log = LoggerFactory.getLogger(ArgosTranslateService.class);
    private final JdbcClient jdbcClient;
    private final RestClient restClient;
    private final String argosEndpoint;

    public ArgosTranslateService(
            JdbcClient jdbcClient,
            RestClient.Builder restClientBuilder,
            @Value("${cookiebot.translate.url:http://localhost:5000/translate}") String argosEndpoint) {
        this.jdbcClient = jdbcClient;
        this.restClient = restClientBuilder.build();
        this.argosEndpoint = argosEndpoint;
    }

    public String translate(String text, String sourceLang, String targetLang) {
        String hashKey = generateHash(text, sourceLang, targetLang);

        // 1. Tenta recuperar do Cache Relacional (Hot-Path via JdbcClient)
        var cachedTranslation = jdbcClient.sql("SELECT translated_text FROM translation_cache WHERE hash_key = :hashKey")
                .param("hashKey", hashKey)
                .query(String.class)
                .optional();

        if (cachedTranslation.isPresent()) {
            return cachedTranslation.get();
        }

        // 2. Cache Miss: Chama o contêiner local do Argos Translate
        try {
            ArgosResponse response = restClient.post()
                    .uri(argosEndpoint)
                    .header("Content-Type", "application/json")
                    .body(new ArgosRequest(text, sourceLang, targetLang))
                    .retrieve()
                    .body(ArgosResponse.class);

            if (response != null && response.translatedText() != null) {
                // 3. Salva a nova tradução no banco para a próxima vez
                saveToCache(hashKey, sourceLang, targetLang, text, response.translatedText());
                return response.translatedText();
            }
        } catch (Exception e) {
            log.error("Falha ao comunicar com Argos Translate local", e);
        }
        
        // Em caso de falha, retorna o texto original graciosamente
        return text;
    }

    private void saveToCache(String hashKey, String source, String target, String original, String translated) {
        jdbcClient.sql("""
                INSERT INTO translation_cache (hash_key, source_lang, target_lang, original_text, translated_text) 
                VALUES (:hash, :source, :target, :original, :translated) 
                ON CONFLICT (hash_key) DO NOTHING
                """)
                .param("hash", hashKey)
                .param("source", source)
                .param("target", target)
                .param("original", original)
                .param("translated", translated)
                .update();
    }

    private String generateHash(String text, String source, String target) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = source + "_" + target + "_" + text;
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 ausente na JVM", e);
        }
    }

    // Records nativos para o Payload JSON
    private record ArgosRequest(String q, String source, String target) {}
    private record ArgosResponse(String translatedText) {}
}