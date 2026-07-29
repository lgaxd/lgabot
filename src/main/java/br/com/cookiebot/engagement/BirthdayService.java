package br.com.cookiebot.engagement;

import br.com.cookiebot.storage.MinioStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Joiner;

@Service
@EnableScheduling
public class BirthdayService {

    private static final Logger log = LoggerFactory.getLogger(BirthdayService.class);
    private final JdbcClient jdbcClient;
    private final MinioStorageService minioService;

    public BirthdayService(JdbcClient jdbcClient, MinioStorageService minioService) {
        this.jdbcClient = jdbcClient;
        this.minioService = minioService;
    }

    private record BirthdayUser(Long userId, String avatarUrl) {}

    // Roda todos os dias às 00:00
    @Scheduled(cron = "0 0 0 * * *")
    public void processDailyBirthdays() {
        int currentMonth = LocalDate.now().getMonthValue();
        int currentDay = LocalDate.now().getDayOfMonth();

        List<BirthdayUser> birthdayUsers = jdbcClient.sql("""
                SELECT user_id, avatar_url FROM user_birthdays 
                WHERE EXTRACT(MONTH FROM birth_date) = :month 
                AND EXTRACT(DAY FROM birth_date) = :day
                """)
                .param("month", currentMonth)
                .param("day", currentDay)
                .query(BirthdayUser.class)
                .list();

        if (birthdayUsers.isEmpty()) return;

        log.info("Processando colagem para {} aniversariantes do dia.", birthdayUsers.size());
        
        // O novo Joiner do Java 26. Retorna a lista de resultados e aborta tudo se um falhar!
        try (var scope = StructuredTaskScope.open(Joiner.<File>allSuccessfulOrThrow())) {
            
            // Eliminamos o Stream do Hot-Path a favor de um loop for-each limpo
            for (BirthdayUser u : birthdayUsers) {
                if (u.avatarUrl() != null) {
                    scope.fork(() -> minioService.downloadAvatar(u.avatarUrl()));
                }
            }
            
            // O join() agora aguarda as Virtual Threads e retorna magicamente a List<File> pronta!
            List<File> avatarFiles = scope.join();
                    
            log.info("Sucesso! {} avatares baixados concorrentemente. Prontos para edição...", avatarFiles.size());
            // Aqui conectaríamos o ImageMagick CLI (Fase 10)
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Processamento de aniversários interrompido.", e);
        } catch (Exception e) {
            // Pega ExecutionException (lançada pelo Joiner) caso alguma subtask falhe
            log.error("Falha na rede ou servidor. Cancelando download de avatares.", e);
        }
    }
}