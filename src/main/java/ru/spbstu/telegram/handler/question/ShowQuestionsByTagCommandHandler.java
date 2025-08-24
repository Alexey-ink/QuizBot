package ru.spbstu.telegram.handler.question;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.spbstu.telegram.handler.CommandHandler;
import ru.spbstu.model.Question;
import ru.spbstu.service.QuestionService;
import ru.spbstu.telegram.sender.MessageSender;

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
        String[] parts = text.split(" ");
        
        if (parts.length < 2) {
            messageSender.sendMessage(update.getMessage().getChatId(),
                "❌ Укажите тег.\nИспользование: `/show_questions_by_tag <тег>`");
            return;
        }
        if (parts.length > 2) {
            messageSender.sendMessage(update.getMessage().getChatId(),
                    "❌ Укажите один тег без пробелов.\nИспользование: `/show_questions_by_tag <тег>`");
            return;
        }
        String tagName = parts[1].trim();
        Long telegramId = update.getMessage().getFrom().getId();
        String tagNameForMarkdown = messageSender.escapeTagForMarkdown(tagName);

        if (!questionService.tagExists(tagName)) {
            messageSender.sendMessage(update.getMessage().getChatId(),
                "❌ Тег #" + tagNameForMarkdown + " не существует.\n\n" +
                "🏷️ **Создать тег:** `/add_tag " + tagName + "`\n" +
                "📋 **Просмотр тегов:** `/list_tags`");
            return;
        }

        List<Question> questions = questionService.getQuestionsByTag(tagName);

        if (questions.isEmpty()) {
            messageSender.sendMessage(update.getMessage().getChatId(),
                "ℹ️ По тегу #" + tagNameForMarkdown + " пока нет вопросов.");
            return;
        }

        StringBuilder response = new StringBuilder();
        response.append("📋 Список вопросов по тегу #").append(tagNameForMarkdown).append(" (всего ").append(questions.size()).append("):\n\n");

        for (Question question : questions) {
            String questionText = question.getText();
            if (questionText.length() > 50) {
                questionText = questionText.substring(0, 47) + "...";
            }
            response.append("• 🆔: `").append(question.getId()).append("` \n  \uD83D\uDCDA «").append(questionText).append("»\n\n");
        }

        messageSender.sendMessage(update.getMessage().getChatId(), response.toString());
    }
}
