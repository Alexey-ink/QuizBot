package ru.spbstu.telegram.handler;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.spbstu.telegram.sender.MessageSender;

@Component
public abstract class CommandHandler {
    protected final MessageSender messageSender;

    protected CommandHandler(MessageSender messageSender) {
        this.messageSender = messageSender;
    }

    public abstract String getCommand();
    public abstract void handle(Update update);

    public String getDescription() {
        return "Команда бота";
    }
}

