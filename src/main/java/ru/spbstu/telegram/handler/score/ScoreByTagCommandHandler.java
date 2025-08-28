package ru.spbstu.telegram.handler.score;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.spbstu.telegram.handler.CommandHandler;
import ru.spbstu.telegram.sender.MessageSender;
import ru.spbstu.service.ScoreByTagService;

@Component
public class ScoreByTagCommandHandler extends CommandHandler {

    private final ScoreByTagService scoreByTagService;

    public ScoreByTagCommandHandler(MessageSender messageSender,
                                    ScoreByTagService scoreByTagService) {
        super(messageSender);
        this.scoreByTagService = scoreByTagService;
    }

    @Override
    public String getCommand() {
        return "/score_by_tag";
    }

    @Override
    public void handle(Update update) {
        Long telegramId = update.getMessage().getFrom().getId();
        String text = update.getMessage().getText();

        logger.info("Обработка команды /score_by_tag от пользователя {}: {}", telegramId, text);
        try {
            String[] parts = text.split(" ");


            if (parts.length < 2) {
                logger.warn("Не указан тег для команды score_by_tag пользователем {}", telegramId);
                messageSender.sendMessage(update.getMessage().getChatId(),
                        "❌ Укажите тег.\nИспользование: `/score_by_tag <тег>`");
                return;
            }
            if (parts.length > 2) {
                logger.warn("Слишком много параметров для команды score_by_tag от пользователя {}: {}",
                        telegramId, text);
                messageSender.sendMessage(update.getMessage().getChatId(),
                        "❌ Укажите один тег без пробелов.\nИспользование: `/score_by_tag <тег>`");
                return;
            }
            String tagName = parts[1].trim();
            logger.debug("Запрос счета по тегу '{}' для пользователя {}", tagName, telegramId);

            if (!scoreByTagService.tagExists(tagName)) {
                logger.warn("Тег '{}' не существует (пользователь {})", tagName, telegramId);
                messageSender.sendMessage(update.getMessage().getChatId(),
                        "❌ Тег #" + messageSender.escapeTagForMarkdown(tagName) + " не существует.\n\n" +
                                "🏷️ **Создайте тег:** `/add_tag " + tagName + "`\n" +
                                "📋 **Просмотр тегов:** `/list_tags`");
                return;
            }

            Integer score = scoreByTagService.getScoreByUserIdAndTagName(telegramId, tagName);
            logger.debug("Счет пользователя {} по тегу '{}': {}", telegramId, tagName, score);

            messageSender.sendMessage(telegramId, "\uD83C\uDFC6 Ваш счет по тегу #" +
                    messageSender.escapeTagForMarkdown(tagName) + ": " + score);
        } catch (Exception e) {
            logger.error("Ошибка при получении счета по тегу пользователем {}: {}",
                    telegramId, e.getMessage(), e);
            messageSender.sendMessage(telegramId, "❌ Произошла ошибка при получении счета");
        }
    }

    @Override
    public String getDescription() {
        return "Показать количество баллов по тегу";
    }
}
