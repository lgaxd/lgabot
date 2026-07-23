package br.com.cookiebot.moderation;

import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class SvgCaptchaGenerator {

    public record CaptchaResult(String answer, byte[] svgImage) {}

    public CaptchaResult generate() {
        // Uso estrito do ThreadLocalRandom para evitar contenção
        int a = ThreadLocalRandom.current().nextInt(1, 10);
        int b = ThreadLocalRandom.current().nextInt(1, 10);
        String answer = String.valueOf(a + b);

        // Geração de primitivas SVG diretamente em String (Headless)
        String svg = """
            <svg width="200" height="100" xmlns="http://www.w3.org/2000/svg">
                <rect width="100%%" height="100%%" fill="#f0f0f0" />
                <text x="50" y="55" font-family="monospace" font-size="30" fill="#333">%d + %d = ?</text>
            </svg>
            """.formatted(a, b);

        return new CaptchaResult(answer, svg.getBytes(StandardCharsets.UTF_8));
    }
}