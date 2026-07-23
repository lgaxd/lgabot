package br.com.cookiebot.cache.domain;

import java.util.List;

// Record imutável que representará a estrutura do JSONB no banco de dados
public record RulesMatrix(String welcomeText, List<String> agreeButtons) {}