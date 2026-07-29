package br.com.cookiebot.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class MinioStorageService {

    private static final Logger log = LoggerFactory.getLogger(MinioStorageService.class);
    private final RestClient restClient;

    public MinioStorageService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    public File downloadAvatar(String avatarUrl) {
        try {
            byte[] imageBytes = restClient.get()
                    .uri(avatarUrl)
                    .retrieve()
                    .body(byte[].class);

            if (imageBytes == null) {
                throw new IllegalStateException("Avatar retornado vazio: " + avatarUrl);
            }

            // Grava temporariamente no volume tmpfs (RAM)
            Path tempFilePath = Path.of("/tmp", "avatar_" + UUID.randomUUID() + ".jpg");
            Files.write(tempFilePath, imageBytes);
            return tempFilePath.toFile();

        } catch (Exception e) {
            log.error("Erro ao baixar avatar do MinIO local: {}", avatarUrl, e);
            throw new RuntimeException("Falha no download da mídia", e);
        }
    }
}