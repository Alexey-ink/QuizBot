package ru.spbstu.handler;

import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.bots.AbsSender;

public interface CommandHandler {
    String getCommand();
    void handle(Update update, AbsSender sender);
    
    /**
     * Возвращает описание команды для отображения в подсказках
     * @return описание команды
     */
    default String getDescription() {
        return "Команда бота";
    }
}

