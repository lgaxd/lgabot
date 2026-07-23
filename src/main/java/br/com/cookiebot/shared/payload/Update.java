package br.com.cookiebot.shared.payload;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Update(
        @JsonProperty("update_id") Long updateId, 
        Message message, 
        @JsonProperty("callback_query") CallbackQuery callbackQuery
) {}