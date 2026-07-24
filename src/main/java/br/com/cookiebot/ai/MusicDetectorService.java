package br.com.cookiebot.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
public class MusicDetectorService {

    private static final Logger log = LoggerFactory.getLogger(MusicDetectorService.class);
    
    // Proteção inegociável exigida pela arquitetura: Máximo de 5 processos CLI simultâneos
    private final Semaphore cliSemaphore = new Semaphore(5);

    public String detectMusic(String absoluteFilePath) {
        boolean acquired = false;
        try {
            acquired = cliSemaphore.tryAcquire(10, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("Capacidade máxima de processos Shazam atingida. Requisição descartada.");
                return "O sistema está processando muitos áudios no momento.";
            }

            ProcessBuilder builder = new ProcessBuilder("shazamio", absoluteFilePath);
            Process process = builder.start();
            
            // Graças ao Project Loom as Virtual Threads podem fazer I/O bloqueante perfeitamente
            byte[] outputBytes = process.getInputStream().readAllBytes();
            int exitCode = process.waitFor();
            
            if (exitCode != 0) {
                log.error("Processo shazamio falhou com código: {}", exitCode);
                return "Não consegui identificar a música.";
            }
            
            return new String(outputBytes, StandardCharsets.UTF_8).trim();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Processamento interrompido.";
        } catch (IOException e) {
            log.error("Erro de I/O ao invocar o ShazamIO nativo", e);
            return "Erro interno de leitura.";
        } finally {
            if (acquired) {
                cliSemaphore.release();
            }
        }
    }
}