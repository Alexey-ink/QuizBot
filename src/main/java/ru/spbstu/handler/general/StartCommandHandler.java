package ru.spbstu.handler.general;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.spbstu.handler.CommandHandler;
import ru.spbstu.service.UserService;

@Component
public class StartCommandHandler implements CommandHandler {
    private final UserService userService;

    public StartCommandHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public String getCommand() {
        return "/start";
    }

    @Override
    public String getDescription() {
        return "Запустить бота";
    }

    @Override
    public void handle(Update update, AbsSender sender) {
        var tgUser = update.getMessage().getFrom();
        userService.getOrCreateUser(tgUser.getId(), tgUser.getUserName());

        String text = "Добро пожаловать, " +
                (tgUser.getUserName() != null ? "@" + tgUser.getUserName() : "гость") + "!\n" +
                "Этот бот поможет тебе создавать вопросы и проходить викторины!\n\n" +
                "💡 Начни с команды /help, чтобы узнать все возможности";

        sendMessage(sender, update.getMessage().getChatId(), text);
    }
}
