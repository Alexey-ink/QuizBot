package ru.spbstu.handler.score;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.bots.AbsSender;
import ru.spbstu.handler.CommandHandler;
import ru.spbstu.service.UserService;

@Component
public class ScoreCommandHandler implements CommandHandler {

    private final UserService userService;

    public ScoreCommandHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public String getCommand() {
        return "/score";
    }

    @Override
    public void handle(Update update, AbsSender sender) {
        Long userId = update.getMessage().getFrom().getId();
        sendMessage(sender, userId, "🏆 *Ваш счет:* " + userService.getUser(userId).getScore() + " баллов");
    }

    @Override
    public String getDescription() {
        return "Показать общее количество баллов";
    }
}
