package ru.spbstu.handler;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.spbstu.session.Session;
import ru.spbstu.utils.SessionManager;
import ru.spbstu.session.QuestionSession;
import ru.spbstu.session.SessionType;
import ru.spbstu.session.DeleteTagConfirmationSession;

@Component
public class SessionMessageHandler implements CommandHandler {
    private final SessionManager sessionManager;
    private final AddQuestionCommandHandler addQuestionHandler;
    private final DeleteQuestionCommandHandler deleteQuestionHandler;
    private final DeleteTagCommandHandler deleteTagHandler;

    public SessionMessageHandler(SessionManager sessionManager, AddQuestionCommandHandler addQuestionHandler, DeleteQuestionCommandHandler deleteQuestionHandler, DeleteTagCommandHandler deleteTagHandler) {
        this.sessionManager = sessionManager;
        this.addQuestionHandler = addQuestionHandler;
        this.deleteQuestionHandler = deleteQuestionHandler;
        this.deleteTagHandler = deleteTagHandler;

    public SessionMessageHandler(SessionManager sessionManager, AddQuestionCommandHandler addQuestionHandler) {
        this.sessionManager = sessionManager;
        this.addQuestionHandler = addQuestionHandler;
    }

    @Override
    public String getCommand() {
        return "default";
    }

    @Override
    public void handle(Update update, AbsSender sender) {
        var userId = update.getMessage().getFrom().getId();
        Session session = sessionManager.getSession(userId);
        if (session instanceof QuestionSession) {
            addQuestionHandler.handle(update, sender);
        }
        else if (session != null && session.getType() == SessionType.DELETE_CONFIRMATION) {
            handleDeleteConfirmation(update, sender, userId);
        }
        else if (session != null && session.getType() == SessionType.DELETE_TAG_CONFIRMATION) {
            handleDeleteTagConfirmation(update, sender, userId);
        }
        else { // Если нет никаких сессий
            try {
                SendMessage message = new SendMessage();
                message.setChatId(String.valueOf(update.getMessage().getChatId()));
                message.setText("Неизвестная команда.\nВведите /help для просмотра команд");
                message.enableMarkdown(true);
                sender.execute(message);
        else { // Если нет никаких сессий
            try {
                sender.execute(new SendMessage(
                        String.valueOf(update.getMessage().getChatId()), "Неизвестная команда.\n" +
                        "Введите команду /help"));
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleDeleteConfirmation(Update update, AbsSender sender, Long userId) {
        String text = update.getMessage().getText().toLowerCase().trim();
        
        if (text.equals("да") || text.equals("yes") || text.equals("y")) {
            deleteQuestionHandler.confirmDeletion(userId, true);
            sendMessage(sender, update.getMessage().getChatId(), "✅ Вопрос удален.");
        } else if (text.equals("нет") || text.equals("no") || text.equals("n")) {
            deleteQuestionHandler.confirmDeletion(userId, false);
            sendMessage(sender, update.getMessage().getChatId(), "❗ Отменено.");
        } else {
            sendMessage(sender, update.getMessage().getChatId(), 
                "Пожалуйста, ответьте «Да» или «Нет» для подтверждения удаления вопроса.");
        }
    }

    private void handleDeleteTagConfirmation(Update update, AbsSender sender, Long userId) {
        String text = update.getMessage().getText().toLowerCase().trim();
        
        if (text.equals("да") || text.equals("yes") || text.equals("y")) {
            deleteTagHandler.confirmDeletion(userId, true);
            sendMessage(sender, update.getMessage().getChatId(), "✅ Тег удален.");
        } else if (text.equals("нет") || text.equals("no") || text.equals("n")) {
            deleteTagHandler.confirmDeletion(userId, false);
            sendMessage(sender, update.getMessage().getChatId(), "❗ Отменено.");
        } else {
            sendMessage(sender, update.getMessage().getChatId(), 
                "Пожалуйста, ответьте «Да» или «Нет» для подтверждения удаления тега.");
        }
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
}
