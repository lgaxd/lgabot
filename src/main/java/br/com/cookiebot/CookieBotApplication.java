package br.com.cookiebot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CookieBotApplication {

    public static void main(String[] args) {
        // Inicialização estrita. Logs em INFO demonstrarão se as Virtual Threads
        // e o Flyway estão operando corretamente durante o boot.
        SpringApplication.run(CookieBotApplication.class, args);
    }
}