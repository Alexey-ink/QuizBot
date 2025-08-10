package ru.spbstu.handler;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Component
public class DefaultHandler implements CommandHandler {
    @Override
    public String getCommand() {
        return "default";
    }

    @Override
    public void handle(Update update, AbsSender sender) {
        String chatId = String.valueOf(update.getMessage().getChatId());
        String text = update.getMessage().getText();
        try {
            sender.execute(new SendMessage(chatId, text));
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}