package br.com.cookiebot.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class SpeechToTextService {

    private static final Logger log = LoggerFactory.getLogger(SpeechToTextService.class);
    private final RestClient restClient;
    private final String sttEndpoint;

    public SpeechToTextService(
            RestClient.Builder restClientBuilder,
            @Value("${cookiebot.stt.url}") String sttEndpoint) {
        this.restClient = restClientBuilder.build();
        this.sttEndpoint = sttEndpoint;
    }

    public String transcribe(byte[] oggAudioBuffer) {
        try {
            // Envia o array de bytes diretamente na memória sem gravar disco intermediário
            return restClient.post()
                    .uri(sttEndpoint)
                    .header("Content-Type", "audio/ogg")
                    .body(oggAudioBuffer)
                    .retrieve()
                    .body(String.class);
                    
        } catch (Exception e) {
            log.error("Falha na transcrição do áudio via Whisper.cpp", e);
            return "[Erro na transcrição do áudio]";
        }
    }
}