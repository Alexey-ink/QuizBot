package ru.spbstu.bot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class QuizBot extends TelegramLongPollingBot {

    private final String token;
    private final String username;
    private final UpdateDispatcher dispatcher;

    @Autowired
    public QuizBot(
            @Value("${telegram.bot.token}") String token,
            @Value("${telegram.bot.username}") String username,
            UpdateDispatcher dispatcher
    ) {
        this.token = token;
        this.username = username;
        this.dispatcher = dispatcher;
    }

    @Override
    public String getBotToken() {
        return token;
    }

    @Override
    public String getBotUsername() {
        return username;
    }

    @Override
    public void onUpdateReceived(Update update) {
        dispatcher.dispatch(update, this);
    }
}
