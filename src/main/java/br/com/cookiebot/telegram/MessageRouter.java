package br.com.cookiebot.telegram;

import br.com.cookiebot.shared.payload.Update;
import br.com.cookiebot.shared.payload.Message;
import br.com.cookiebot.shared.payload.CallbackQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MessageRouter {

    private static final Logger log = LoggerFactory.getLogger(MessageRouter.class);

    public void route(Update update) {
        switch (update) {
            // 1. Comando de texto (verifica se o Callback é nulo e o texto começa com "/")
            case Update(var id, Message(var msgId, var chat, var from, String text), var cb) 
                    when cb == null && text != null && text.startsWith("/") ->
                log.info("Comando de moderação recebido: {} no chat {}", text, chat.id());
                
            // 2. Ação de Botão (CallbackQuery presente, Message direta nula)
            case Update(var id, var msg, CallbackQuery(var cbId, var from, var cbMsg, var data)) 
                    when msg == null ->
                log.info("Ação de botão (Callback) recebida: {} do usuário {}", data, from.id());
                
            // 3. Mensagem comum no chat (texto normal, imagens, etc.)
            case Update(var id, Message msg, var cb) 
                    when cb == null ->
                log.info("Mensagem comum recebida no chat {}. Roteando para regras de grupo.", msg.chat().id());
                
            // 4. Estruturas desconhecidas
            default ->
                log.warn("Update ignorado ou formato de mídia não suportado no momento.");
        }
    }
}