package br.com.cookiebot.shared.payload;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Message(
        @JsonProperty("message_id") Long messageId, 
        Chat chat, 
        User from, 
        String text
) {}