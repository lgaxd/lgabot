package br.com.cookiebot.shared.payload;

public record CallbackQuery(String id, User from, Message message, String data) {}