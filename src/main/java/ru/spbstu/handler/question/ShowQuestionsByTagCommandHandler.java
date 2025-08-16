package ru.spbstu.handler.question;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
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

        // Проверяем существование тега
        if (!questionService.tagExists(telegramId, tagName)) {
            sendMessage(sender, update.getMessage().getChatId(),
                "❌ Тег «" + tagName + "» не существует.\n\n" +
                "🏷️ **Создайте тег:** `/add_tag " + tagName + "`\n" +
                "📋 **Просмотр тегов:** `/list_tags`");
            return;
        }

        // Получаем вопросы по тегу
        List<Question> questions = questionService.getQuestionsByTag(telegramId, tagName);

        if (questions.isEmpty()) {
            sendMessage(sender, update.getMessage().getChatId(),
                "ℹ️ По тегу «" + tagName + "» пока нет вопросов.");
            return;
        }

        // Формируем ответ
        StringBuilder response = new StringBuilder();
        response.append("📋 Список вопросов по тегу «").append(tagName).append("» (всего ").append(questions.size()).append("):\n\n");

        for (Question question : questions) {
            String questionText = question.getText();
            if (questionText.length() > 50) {
                questionText = questionText.substring(0, 47) + "...";
            }
            response.append("• 🆔: ").append(question.getId()).append(" — ").append(questionText).append("\n");
        }

        sendMessage(sender, update.getMessage().getChatId(), response.toString());
    }

    private void sendMessage(AbsSender sender, Long chatId, String text) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));
            message.setText(text);
            message.enableMarkdown(true);
            sender.execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
