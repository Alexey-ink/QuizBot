package ru.spbstu.telegram.handler.question;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.spbstu.telegram.handler.CommandHandler;
import ru.spbstu.telegram.sender.MessageSender;
import ru.spbstu.service.QuestionService;
import ru.spbstu.dto.QuestionDto;

import java.util.List;

@Component
public class ShowQuestionsByTagCommandHandler extends CommandHandler {
    private final QuestionService questionService;

    public ShowQuestionsByTagCommandHandler(MessageSender messageSender,
                                            QuestionService questionService) {
        super(messageSender);
        this.questionService = questionService;
    }

    @Override
    public String getCommand() {
        return "/show_questions_by_tag";
    }

    @Override
    public String getDescription() {
        return "Показать все вопросы по указанному тегу";
    }

    @Override
    public void handle(Update update) {
        String text = update.getMessage().getText();
        Long telegramId = update.getMessage().getFrom().getId();
        try {
            String[] parts = text.split(" ");

            logger.info("Обработка команды show_questions_by_tag от пользователя {}: {}",
                    telegramId, text);

            if (parts.length < 2) {
                messageSender.sendMessage(telegramId,
                        "❌ Укажите тег.\nИспользование: `/show_questions_by_tag <тег>`");
                return;
            }
            if (parts.length > 2) {
                messageSender.sendMessage(telegramId,
                        "❌ Укажите один тег без пробелов.\nИспользование: `/show_questions_by_tag <тег>`");
                return;
            }
            String tagName = parts[1].trim();
            String tagNameForMarkdown = messageSender.escapeTagForMarkdown(tagName);

            if (!questionService.tagExists(tagName)) {
                logger.warn("Тег '{}' не существует (пользователь {})", tagName, telegramId);
                messageSender.sendMessage(telegramId,
                        "❌ Тег #" + tagNameForMarkdown + " не существует.\n\n" +
                                "🏷️ **Создать тег:** `/add_tag " + tagName + "`\n" +
                                "📋 **Просмотр тегов:** `/list_tags`");
                return;
            }

            List<QuestionDto> questions = questionService.getQuestionsByTag(tagName);

            if (questions.isEmpty()) {
                messageSender.sendMessage(telegramId,
                        "ℹ️ По тегу #" + tagNameForMarkdown + " пока нет вопросов.");
                return;
            }

            logger.debug("Найдено {} вопросов по тегу '{}' для пользователя {}",
                    questions.size(), tagName, telegramId);
            StringBuilder response = new StringBuilder();
            response.append("📋 Список вопросов по тегу #").append(tagNameForMarkdown).append(" (всего ").append(questions.size()).append("):\n\n");

            for (QuestionDto question : questions) {
                String questionText = question.text()
                        .replace("_", "\\_")
                        .replace("*", "\\*")
                        .replace("`", "\\`");

                if (questionText.length() > 50) {
                    questionText = questionText.substring(0, 47) + "...";
                }
                response.append("• 🆔: `").append(question.id())
                        .append("` \n  \uD83D\uDCDA «").append(questionText).append("»\n\n");
            }

            messageSender.sendMessage(telegramId, response.toString());
            logger.info("Список вопросов по тегу '{}' отправлен пользователю {}", tagName, telegramId);
        } catch (Exception e) {
            logger.error("Ошибка при обработке show_questions_by_tag пользователем {}: {}",
                    telegramId, e.getMessage(), e);
            messageSender.sendMessage(telegramId,
                    "❌ Произошла ошибка при получении списка вопросов");
        }
    }
}
