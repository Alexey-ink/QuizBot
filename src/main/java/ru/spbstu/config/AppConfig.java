package ru.spbstu.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import ru.spbstu.QuizBot;

@Configuration
public class AppConfig {

    private final Dotenv dotenv = Dotenv.load();

    @Bean
    public QuizBot quizBot() {
        String token = dotenv.get("TELEGRAM_BOT_TOKEN");
        String username = dotenv.get("TELEGRAM_BOT_USERNAME");
        return new QuizBot(token, username);
    }

    @Bean
    public TelegramBotsApi telegramBotsApi(QuizBot bot) throws Exception {
        TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
        api.registerBot(bot);
        return api;
    }
}