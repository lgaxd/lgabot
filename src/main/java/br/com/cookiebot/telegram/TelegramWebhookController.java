package br.com.cookiebot.telegram;

import br.com.cookiebot.shared.payload.Update;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/webhook")
public class TelegramWebhookController {

    private final TelegramTokenValidator tokenValidator;
    private final MessageRouter messageRouter;
    private final ExecutorService virtualThreadExecutor;

    public TelegramWebhookController(TelegramTokenValidator tokenValidator, MessageRouter messageRouter) {
        this.tokenValidator = tokenValidator;
        this.messageRouter = messageRouter;
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @PostMapping("/telegram")
    public ResponseEntity<Void> receiveUpdate(
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secretToken,
            @RequestBody Update update) {

        if (!tokenValidator.isValid(secretToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        virtualThreadExecutor.submit(() -> messageRouter.route(update));

        return ResponseEntity.ok().build();
    }
}