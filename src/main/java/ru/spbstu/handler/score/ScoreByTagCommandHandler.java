package ru.spbstu.handler.score;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.bots.AbsSender;
import ru.spbstu.handler.CommandHandler;
import ru.spbstu.service.ScoreByTagService;

@Component
public class ScoreByTagCommandHandler implements CommandHandler {

    private final ScoreByTagService scoreByTagService;

    public ScoreByTagCommandHandler(ScoreByTagService scoreByTagService) {
        this.scoreByTagService = scoreByTagService;
    }

    @Override
    public String getCommand() {
        return "/score_by_tag";
    }

    @Override
    public void handle(Update update, AbsSender sender) {
        String text = update.getMessage().getText();
        String[] parts = text.split(" ");

        if (parts.length < 2) {
            sendMessage(sender, update.getMessage().getChatId(),
                    "❌ Укажите тег.\nИспользование: `/score_by_tag <тег>`");
            return;
        }
        if (parts.length > 2) {
            sendMessage(sender, update.getMessage().getChatId(),
                    "❌ Укажите один тег без пробелов.\nИспользование: `/score_by_tag <тег>`");
            return;
        }
        String tagName = parts[1].trim();
        Long telegramId = update.getMessage().getFrom().getId();

        if (!scoreByTagService.tagExists(telegramId, tagName)) {
            sendMessage(sender, update.getMessage().getChatId(),
                    "❌ Тег «" + tagName + "» не существует.\n\n" +
                            "🏷️ **Создайте тег:** `/add_tag " + tagName + "`\n" +
                            "📋 **Просмотр тегов:** `/list_tags`");
            return;
        }

        Integer score = scoreByTagService.getScoreByUserIdAndTagName(telegramId, tagName);

        sendMessage(sender, telegramId, "\uD83C\uDFC6 Ваш счет по тегу #" +
                tagName + ": " + score);
    }

    @Override
    public String getDescription() {
        return "Показать количество баллов по тегу";
    }
}
