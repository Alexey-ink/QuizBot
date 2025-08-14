package ru.spbstu.handler;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.spbstu.model.Question;
import ru.spbstu.service.QuestionService;
import ru.spbstu.utils.SessionManager;
import ru.spbstu.session.SessionType;
import ru.spbstu.session.DeleteConfirmationSession;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Component
public class DeleteQuestionCommandHandler implements CommandHandler {
    private final QuestionService questionService;
    private final SessionManager sessionManager;
    
    // Хранилище ожидающих подтверждения удалений
    private final Map<Long, Long> pendingDeletions = new ConcurrentHashMap<>();

    public DeleteQuestionCommandHandler(QuestionService questionService, SessionManager sessionManager) {
        this.questionService = questionService;
        this.sessionManager = sessionManager;
    }

    @Override
    public String getCommand() {
        return "/delete_question";
    }

    @Override
    public void handle(Update update, AbsSender sender) {
        String text = update.getMessage().getText();
        String[] parts = text.split(" ", 2);
        
        if (parts.length < 2) {
            sendMessage(sender, update.getMessage().getChatId(), 
                "❌ Укажите ID вопроса.\nИспользование: /delete_question <ID>");
            return;
        }

        try {
            Long questionId = Long.parseLong(parts[1].trim());
            Long telegramId = update.getMessage().getFrom().getId();
            
            // Проверяем существование вопроса
            Question question = questionService.getQuestionById(questionId);
            if (question == null) {
                sendMessage(sender, update.getMessage().getChatId(),
                    "❌ Вопрос с ID " + questionId + " не существует.");
                return;
            }
            
            // Проверяем владельца вопроса
            if (!questionService.isQuestionOwner(telegramId, questionId)) {
                sendMessage(sender, update.getMessage().getChatId(),
                    "❌ Вопрос с ID " + questionId + " создан другим пользователем.");
                return;
            }
            
            // Запрашиваем подтверждение
            String questionText = question.getText();
            if (questionText.length() > 50) {
                questionText = questionText.substring(0, 47) + "...";
            }
            
            String confirmationMessage = "❗ Удалить вопрос: «" + questionText + "»? (Да/Нет)";
            sendMessage(sender, update.getMessage().getChatId(), confirmationMessage);
            
            // Сохраняем ожидающее удаление
            pendingDeletions.put(telegramId, questionId);
            
            // Создаем сессию для ожидания ответа
            sessionManager.setSession(telegramId, new DeleteConfirmationSession());
            
        } catch (NumberFormatException e) {
            sendMessage(sender, update.getMessage().getChatId(),
                "❌ Неверный формат ID. ID должен быть числом.");
        }
    }

    public void confirmDeletion(Long telegramId, boolean confirmed) {
        Long questionId = pendingDeletions.get(telegramId);
        if (questionId != null) {
            if (confirmed) {
                questionService.deleteQuestion(questionId);
                pendingDeletions.remove(telegramId);
            } else {
                pendingDeletions.remove(telegramId);
            }
            sessionManager.clear(telegramId);
        }
    }

    public Long getPendingQuestionId(Long telegramId) {
        return pendingDeletions.get(telegramId);
    }

    public void removePendingDeletion(Long telegramId) {
        pendingDeletions.remove(telegramId);
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
