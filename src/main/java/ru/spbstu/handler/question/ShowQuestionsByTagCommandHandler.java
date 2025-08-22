package ru.spbstu.handler.question;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.bots.AbsSender;
import ru.spbstu.handler.CommandHandler;
import ru.spbstu.model.Question;
import ru.spbstu.service.QuestionService;

import java.util.List;

@Component
public class ShowQuestionsByTagCommandHandler implements CommandHandler {
    private final QuestionService questionService;

    public ShowQuestionsByTagCommandHandler(QuestionService questionService) {
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
    public void handle(Update update, AbsSender sender) {
        String text = update.getMessage().getText();
        String[] parts = text.split(" ");
        
        if (parts.length < 2) {
            sendMessage(sender, update.getMessage().getChatId(), 
                "❌ Укажите тег.\nИспользование: `/show_questions_by_tag <тег>`");
            return;
        }
        if (parts.length > 2) {
            sendMessage(sender, update.getMessage().getChatId(),
                    "❌ Укажите один тег без пробелов.\nИспользование: `/show_questions_by_tag <тег>`");
            return;
        }
        String tagName = parts[1].trim();
        Long telegramId = update.getMessage().getFrom().getId();

        if (!questionService.tagExists(telegramId, tagName)) {
            sendMessage(sender, update.getMessage().getChatId(),
                "❌ Тег «" + tagName + "» не существует.\n\n" +
                "🏷️ **Создайте тег:** `/add_tag " + tagName + "`\n" +
                "📋 **Просмотр тегов:** `/list_tags`");
            return;
        }

        List<Question> questions = questionService.getQuestionsByTag(telegramId, tagName);

        if (questions.isEmpty()) {
            sendMessage(sender, update.getMessage().getChatId(),
                "ℹ️ По тегу «" + tagName + "» пока нет вопросов.");
            return;
        }

        StringBuilder response = new StringBuilder();
        response.append("📋 *Список вопросов по тегу «").append(tagName).append("»* (всего ").append(questions.size()).append("):\n\n");

        for (Question question : questions) {
            String questionText = question.getText();
            if (questionText.length() > 50) {
                questionText = questionText.substring(0, 47) + "...";
            }
            response.append("• 🆔: `").append(question.getId()).append("` \n  \uD83D\uDCDA «").append(questionText).append("»\n\n");
        }

        sendMessage(sender, update.getMessage().getChatId(), response.toString());
    }
}
