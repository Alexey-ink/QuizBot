package ru.spbstu.telegram.handler.question;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.spbstu.telegram.handler.CommandHandler;
import ru.spbstu.telegram.sender.MessageSender;
import ru.spbstu.telegram.utils.SessionManager;
import ru.spbstu.telegram.session.DeleteConfirmationSession;
import ru.spbstu.service.QuestionService;
import ru.spbstu.dto.QuestionDto;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Component
public class DeleteQuestionCommandHandler extends CommandHandler {
    private final QuestionService questionService;
    private final SessionManager sessionManager;
    
    // Хранилище ожидающих подтверждения удалений
    private final Map<Long, String> pendingDeletions = new ConcurrentHashMap<>();

    public DeleteQuestionCommandHandler(MessageSender messageSender,
                                        QuestionService questionService,
                                        SessionManager sessionManager) {
        super(messageSender);
        this.questionService = questionService;
        this.sessionManager = sessionManager;
    }

    @Override
    public String getCommand() {
        return "/delete_question";
    }

    @Override
    public String getDescription() {
        return "Удалить вопрос по его ID";
    }

    @Override
    public void handle(Update update) {
        String text = update.getMessage().getText();
        Long telegramId = update.getMessage().getFrom().getId();

        if (sessionManager.hasSession(telegramId)) {
            handleDeleteConfirmation(text, telegramId);
            return;
        }

        String[] parts = text.split(" ");

        if (parts.length < 2) {
            messageSender.sendMessage(telegramId,
                "❌ Укажите ID вопроса.\nИспользование: `/delete_question <ID>`\n\n" +
                    "Команда удалит тег только в том случае, " +
                        "если по этому тегу нет вопросов, созданных другими пользователями");
            return;
        }

        try {
            String questionId = parts[1].trim();

            // Проверяем существование вопроса
            Optional<QuestionDto> question = questionService.getQuestionDtoById(questionId);
            if (question.isEmpty()) {
                messageSender.sendMessage(update.getMessage().getChatId(),
                    "❌ Вопрос с ID " + questionId + " не существует.");
                return;
            }
            
            if (!questionService.isQuestionOwner(telegramId, questionId)) {
                messageSender.sendPlainMessage(update.getMessage().getChatId(),
                    "❌ Вопрос с ID " + questionId + " создан другим пользователем." +
                            "\nВы не можете его удалить!");
                return;
            }
            
            String questionText = question.get().text();
            if (questionText.length() > 50) {
                questionText = questionText.substring(0, 47) + "...";
            }
            
            String confirmationMessage = "❗ Удалить вопрос: «" + questionText + "»? (Да/Нет)";
            messageSender.sendMessage(update.getMessage().getChatId(), confirmationMessage);
            
            pendingDeletions.put(telegramId, questionId);
            
            sessionManager.setSession(telegramId, new DeleteConfirmationSession());
            
        } catch (NumberFormatException e) {
            messageSender.sendMessage(update.getMessage().getChatId(),
                "❌ Неверный формат ID.");
        }
    }

    public void confirmDeletion(Long telegramId, boolean confirmed) {
        String questionId = pendingDeletions.get(telegramId);
        if (questionId != null) {
            if (confirmed) {
                questionService.deleteQuestion(questionId);
                pendingDeletions.remove(telegramId);
            } else {
                pendingDeletions.remove(telegramId);
            }
            sessionManager.clearSession(telegramId);
        }
    }

    private void handleDeleteConfirmation(String text, Long telegramId) {
        if (text.equals("да") || text.equals("yes") || text.equals("y")) {
            confirmDeletion(telegramId, true);
            messageSender.sendMessage(telegramId, "✅ Вопрос удален.");
        } else if (text.equals("нет") || text.equals("no") || text.equals("n")) {
            confirmDeletion(telegramId, false);
            messageSender.sendMessage(telegramId, "❗ Отменено.");
        } else {
            messageSender.sendMessage(telegramId,
                    "Пожалуйста, ответьте «Да» или «Нет» для подтверждения удаления вопроса.");
        }
    }
}
