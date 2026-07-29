package br.com.cookiebot.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class MemeGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(MemeGeneratorService.class);
    private final ExecutorService ioExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public boolean generateMeme(Path inputImagePath, Path outputImagePath, String topText, String bottomText) {
        try {
            // Comando estrito do ImageMagick. O arquivo deve ser processado exclusivamente na RAM (tmpfs)
            ProcessBuilder builder = new ProcessBuilder(
                    "convert", inputImagePath.toString(),
                    "-gravity", "north", "-pointsize", "40", "-fill", "white", "-stroke", "black", "-strokewidth", "2", "-annotate", "+0+20", topText,
                    "-gravity", "south", "-annotate", "+0+20", bottomText,
                    outputImagePath.toString()
            );

            Process process = builder.start();

            // Draining assíncrono para evitar IPC buffer deadlock no host Docker
            ioExecutor.submit(() -> drainStream(process.getInputStream()));
            ioExecutor.submit(() -> drainStream(process.getErrorStream()));

            // Timeout rígido de segurança para proteger a CPU do servidor
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            
            if (!finished) {
                process.destroyForcibly();
                log.error("Processamento do ImageMagick excedeu 10s e foi destruído.");
                Files.deleteIfExists(outputImagePath);
                return false;
            }

            return process.exitValue() == 0;

        } catch (Exception e) {
            log.error("Falha ao invocar o ImageMagick", e);
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void drainStream(InputStream stream) {
        try (stream) {
            stream.readAllBytes(); // Lê e descarta os bytes do stdout/stderr
        } catch (Exception ignored) {
            // Exceções no dreno podem ser silenciadas
        }
    }
}