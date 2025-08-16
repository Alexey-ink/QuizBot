package ru.spbstu.handler;

import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.bots.AbsSender;

public interface CommandHandler {
    String getCommand();
    void handle(Update update, AbsSender sender);

    default String getDescription() {
        return "Команда бота";
    }
}

