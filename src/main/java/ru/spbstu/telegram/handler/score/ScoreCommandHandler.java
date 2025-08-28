package ru.spbstu.telegram.handler.score;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.spbstu.telegram.handler.CommandHandler;
import ru.spbstu.telegram.sender.MessageSender;
import ru.spbstu.service.UserService;

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
        Long telegramId = update.getMessage().getFrom().getId();
        logger.info("Обработка команды /score от пользователя {}", telegramId);

        try {
            Integer score = userService.getScoreIdByTelegramId(telegramId);
            messageSender.sendMessage(telegramId, "🏆 *Ваш счет:* " +
                    score + " баллов");
            logger.debug("Общий счет пользователя {}: {} отправлен в чат", telegramId, score);
        } catch (Exception e) {
            logger.error("Ошибка при получении общего счета пользователя {}: {}",
                    telegramId, e.getMessage(), e);
            messageSender.sendMessage(telegramId, "❌ Произошла ошибка при получении счета");
        }
    }

    @Override
    public String getDescription() {
        return "Показать общее количество баллов";
    }
}
