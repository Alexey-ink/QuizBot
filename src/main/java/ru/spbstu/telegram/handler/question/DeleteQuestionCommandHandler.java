package ru.spbstu.telegram.handler.question;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.spbstu.telegram.handler.CommandHandler;
import ru.spbstu.telegram.sender.MessageSender;
import ru.spbstu.telegram.session.core.SessionType;
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

        logger.info("Обработка команды удаления вопроса от пользователя {}: {}", telegramId, text);

        try {
            if (sessionManager.hasSession(telegramId)) {
                handleDeleteConfirmation(text, telegramId);
                return;
            }

            String[] parts = text.split(" ");

            if (parts.length < 2) {
                logger.debug("Не указан ID вопроса от пользователя {}", telegramId);
                messageSender.sendMessage(telegramId,
                    "❌ Укажите ID вопроса.\nИспользование: `/delete_question <ID>`");
                return;
            }

            String questionId = parts[1].trim();
            logger.debug("Попытка удаления вопроса {} пользователем {}", questionId, telegramId);

            // Проверяем существование вопроса
            Optional<QuestionDto> question = questionService.getQuestionDtoById(questionId);
            if (question.isEmpty()) {
                logger.debug("Вопрос {} не существует (пользователь {})", questionId, telegramId);
                messageSender.sendMessage(update.getMessage().getChatId(),
                    "❌ Вопрос с ID " + questionId + " не существует.");
                return;
            }
            
            if (!questionService.isQuestionOwner(telegramId, questionId)) {
                logger.debug("Попытка удалить чужой вопрос {} пользователем {}", questionId, telegramId);
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
            logger.debug("Запрос подтверждения удаления вопроса {} от пользователя {}",
                    questionId, telegramId);
            
        } catch (NumberFormatException e) {
            messageSender.sendMessage(update.getMessage().getChatId(),
                "❌ Неверный формат ID.");
        } catch (Exception e) {
            logger.error("Ошибка при обработке удаления вопроса пользователем {}: {}",
                    telegramId, e.getMessage(), e);
            messageSender.sendMessage(telegramId,
                    "❌ Произошла ошибка при обработке команды");
        }
    }

    public void confirmDeletion(Long telegramId, boolean confirmed) {
        String questionId = pendingDeletions.get(telegramId);
        if (questionId != null) {
            if (confirmed) {
                try {
                    questionService.deleteQuestion(questionId);
                    logger.info("Вопрос {} удален пользователем {}", questionId, telegramId);
                } catch (Exception e) {
                    logger.error("Ошибка при удалении вопроса {} пользователем {}: {}",
                            questionId, telegramId, e.getMessage(), e);
                }
            } else {
                logger.info("Удаление вопроса {} отменено пользователем {}",
                        questionId, telegramId);
            }
            pendingDeletions.remove(telegramId);
            sessionManager.clearSession(telegramId);
        }
    }

    private void handleDeleteConfirmation(String text, Long telegramId) {

        try {
            if (text.equals("да") || text.equals("yes") || text.equals("y")) {
                logger.debug("Подтверждение удаления от пользователя {}", telegramId);
                confirmDeletion(telegramId, true);
                messageSender.sendMessage(telegramId, "✅ Вопрос удален.");
            } else if (text.equals("нет") || text.equals("no") || text.equals("n")) {
                logger.debug("Отмена удаления от пользователя {}", telegramId);
                confirmDeletion(telegramId, false);
                messageSender.sendMessage(telegramId, "❗ Отменено.");
            } else {
                logger.debug("Неверный ответ подтверждения от пользователя {}: {}", telegramId, text);
                messageSender.sendMessage(telegramId,
                        "Пожалуйста, ответьте «Да» или «Нет» для подтверждения удаления вопроса.");
            }
        } catch (Exception e) {
            logger.error("Ошибка при обработке подтверждения пользователем {}: {}", telegramId, e.getMessage(), e);
            messageSender.sendMessage(telegramId, "❌ Произошла ошибка при обработке подтверждения");
        }
    }
}
