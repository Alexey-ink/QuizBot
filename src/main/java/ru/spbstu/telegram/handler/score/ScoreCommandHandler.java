package ru.spbstu.telegram.handler.score;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.spbstu.telegram.handler.CommandHandler;
import ru.spbstu.service.UserService;
import ru.spbstu.telegram.sender.MessageSender;

@Component
public class ScoreCommandHandler extends CommandHandler {

    private final UserService userService;

    public ScoreCommandHandler(MessageSender messageSender,
                               UserService userService) {
        super(messageSender);
        this.userService = userService;
    }

    @Override
    public String getCommand() {
        return "/score";
    }

    @Override
    public void handle(Update update) {
        Long userId = update.getMessage().getFrom().getId();
        messageSender.sendMessage(userId, "🏆 *Ваш счет:* " + userService.getUser(userId).getScore() + " баллов");
    }

    @Override
    public String getDescription() {
        return "Показать общее количество баллов";
    }
}
