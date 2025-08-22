package ru.spbstu.handler;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.bots.AbsSender;
import ru.spbstu.handler.general.StartCommandHandler;
import ru.spbstu.handler.question.AddQuestionCommandHandler;
import ru.spbstu.handler.question.DeleteQuestionCommandHandler;
import ru.spbstu.handler.question.RandomQuestionCommandHandler;
import ru.spbstu.handler.schedule.ScheduleCommandHandler;
import ru.spbstu.handler.tag.DeleteTagCommandHandler;
import ru.spbstu.session.core.Session;
import ru.spbstu.utils.SessionManager;
import ru.spbstu.session.AddQuestionSession;
import ru.spbstu.session.QuizSession;
import ru.spbstu.session.core.SessionType;

@Component
public class SessionMessageHandler implements CommandHandler {
    private final SessionManager sessionManager;
    private final AddQuestionCommandHandler addQuestionHandler;
    private final RandomQuestionCommandHandler randomQuestionHandler;
    private final DeleteQuestionCommandHandler deleteQuestionHandler;
    private final DeleteTagCommandHandler deleteTagHandler;
    private final ScheduleCommandHandler scheduleCommandHandler;
    private final StartCommandHandler startCommandHandler;

    public SessionMessageHandler(SessionManager sessionManager,
                                 AddQuestionCommandHandler addQuestionHandler,
                                 DeleteQuestionCommandHandler deleteQuestionHandler,
                                 DeleteTagCommandHandler deleteTagHandler,
                                 RandomQuestionCommandHandler randomQuestionHandler,
                                 ScheduleCommandHandler scheduleCommandHandler,
                                 StartCommandHandler startCommandHandler
    ) {
        this.sessionManager = sessionManager;
        this.addQuestionHandler = addQuestionHandler;
        this.deleteQuestionHandler = deleteQuestionHandler;
        this.randomQuestionHandler = randomQuestionHandler;
        this.deleteTagHandler = deleteTagHandler;
        this.scheduleCommandHandler = scheduleCommandHandler;
        this.startCommandHandler = startCommandHandler;
    }

    @Override
    public String getCommand() {
        return "/default";
    }

    @Override
    public void handle(Update update, AbsSender sender) {
        var userId = update.getMessage().getFrom().getId();
        Session session = sessionManager.getSession(userId);

        if(session == null) {
            sendMessage(sender, userId, "Неизвестная команда.\nВведите /help для просмотра команд");
            return;
        }

        if (session instanceof AddQuestionSession) {
            addQuestionHandler.handle(update, sender);
        } else if (session instanceof QuizSession) {
            randomQuestionHandler.handle(update, sender);
        } else if (session.getType() == SessionType.DELETE_CONFIRMATION) {
            handleDeleteConfirmation(update, sender, userId);
        } else if (session.getType() == SessionType.DELETE_TAG_CONFIRMATION) {
            handleDeleteTagConfirmation(update, sender, userId);
        } else if (session.getType() == SessionType.CREATING_SCHEDULE) {
            scheduleCommandHandler.handle(update, sender);
        }else if (session.getType() == SessionType.WAITING_TIMEZONE) {
            startCommandHandler.handle(update, sender);
        } else {
            throw new RuntimeException("UNKNOWN STATE" + session.getType());
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
}

