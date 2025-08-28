package ru.spbstu.telegram.handler.score;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.spbstu.telegram.handler.CommandHandler;
import ru.spbstu.telegram.sender.MessageSender;
import ru.spbstu.service.ScoreByTagService;

@Component
public class ResetScoreCommandHandler extends CommandHandler {
    private final ScoreByTagService scoreByTagService;

    protected ResetScoreCommandHandler(MessageSender messageSender, ScoreByTagService scoreByTagService) {
        super(messageSender);
        this.scoreByTagService = scoreByTagService;
    }

    @Override
    public String getCommand() {
        return "/reset_score";
    }

    @Override
    public String getDescription() {
        return "Сбросить счёт";
    }

    @Override
    public void handle(Update update) {
        Long telegramId = update.getMessage().getFrom().getId();
        logger.info("Обработка команды /reset_score от пользователя {}", telegramId);
        try {
            scoreByTagService.resetScore(telegramId);
            messageSender.sendMessage(telegramId, "🏆 Ваш счет успешно сброшен!");
        } catch (Exception e) {
            logger.error("Ошибка при сбросе счета пользователя {}: {}",
                    telegramId, e.getMessage(), e);
            messageSender.sendMessage(telegramId, "❌ Произошла ошибка при сбросе счета");
        }
    }
}
