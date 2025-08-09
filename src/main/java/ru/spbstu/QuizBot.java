package ru.spbstu;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.spbstu.service.UserService;

public class QuizBot extends TelegramLongPollingBot {

    private final String token;
    private final String username;
    private final UserService userService;

    public QuizBot(String token, String username, UserService userService) {
        this.token = token;
        this.username = username;
        this.userService = userService;
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

            if (text.equals("/start")) {
                var tgUser = update.getMessage().getFrom();
                userService.registerIfNotExists(tgUser.getId(), tgUser.getUserName());
                send(chatId, "Добро пожаловать, " + (tgUser.getUserName() != null ? "@" + tgUser.getUserName() : "гость") + "!");
                return;
            }

            send(chatId, text); // Эхо
        }
    }

    private void send(long chatId, String text) {
        try {
            execute(new SendMessage(String.valueOf(chatId), text));
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
