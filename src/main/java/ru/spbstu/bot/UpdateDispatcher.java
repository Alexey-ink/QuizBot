package ru.spbstu.bot;

import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.bots.AbsSender;
import ru.spbstu.handler.CommandHandler;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class UpdateDispatcher {
    private final Map<String, CommandHandler> handlers;

    public UpdateDispatcher(List<CommandHandler> handlers) {
        this.handlers = handlers.stream()
                .collect(Collectors.toMap(CommandHandler::getCommand, h -> h));
    }

    public void dispatch(Update update, AbsSender sender) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String text = update.getMessage().getText();
            String command = text.split(" ")[0];
            handlers.getOrDefault(command, handlers.get("default"))
                    .handle(update, sender);
        }
    }
}
