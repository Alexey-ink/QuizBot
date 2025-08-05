package ru.spbstu;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class QuizBot extends TelegramLongPollingBot {

    private final String token;
    private final String username;

    public QuizBot(String token, String username) {
        this.token = token;
        this.username = username;
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
        if (update.hasMessage() && update.getMessage().hasText()) {
            String text = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            SendMessage message = new SendMessage(String.valueOf(chatId), text);

            try {
                execute(message); // Эхо
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }
}
