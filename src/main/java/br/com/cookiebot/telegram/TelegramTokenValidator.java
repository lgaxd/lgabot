package br.com.cookiebot.telegram;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;

@Component
public class TelegramTokenValidator {

    private final String secretToken;

    public TelegramTokenValidator(@Value("${telegram.webhook.secret}") String secretToken) {
        this.secretToken = secretToken;
    }

    public boolean isValid(String headerToken) {
        if (headerToken == null || secretToken == null) {
            return false;
        }
        return MessageDigest.isEqual(secretToken.getBytes(), headerToken.getBytes());
    }
}