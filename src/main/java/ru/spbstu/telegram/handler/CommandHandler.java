package ru.spbstu.telegram.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.spbstu.telegram.sender.MessageSender;

@Component
public abstract class CommandHandler {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
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

