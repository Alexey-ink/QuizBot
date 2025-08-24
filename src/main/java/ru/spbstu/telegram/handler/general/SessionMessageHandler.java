package ru.spbstu.telegram.handler.general;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.spbstu.telegram.handler.CommandHandler;
import ru.spbstu.telegram.handler.question.AddQuestionCommandHandler;
import ru.spbstu.telegram.handler.question.DeleteQuestionCommandHandler;
import ru.spbstu.telegram.handler.quiz.RandomCommandHandler;
import ru.spbstu.telegram.handler.schedule.DeleteScheduleCommandHandler;
import ru.spbstu.telegram.handler.schedule.ScheduleCommandHandler;
import ru.spbstu.telegram.handler.tag.DeleteTagCommandHandler;
import ru.spbstu.telegram.sender.MessageSender;
import ru.spbstu.telegram.session.core.Session;
import ru.spbstu.telegram.utils.SessionManager;
import ru.spbstu.telegram.session.AddQuestionSession;
import ru.spbstu.telegram.session.QuizSession;
import ru.spbstu.telegram.session.core.SessionType;

@Component
public class SessionMessageHandler extends CommandHandler {
    private final SessionManager sessionManager;
    private final AddQuestionCommandHandler addQuestionHandler;
    private final RandomCommandHandler randomQuestionHandler;
    private final DeleteQuestionCommandHandler deleteQuestionHandler;
    private final DeleteTagCommandHandler deleteTagHandler;
    private final ScheduleCommandHandler scheduleCommandHandler;
    private final StartCommandHandler startCommandHandler;
    private final DeleteScheduleCommandHandler deleteScheduleCommandHandler;

    public SessionMessageHandler(MessageSender messageSender,
                                 SessionManager sessionManager,
                                 AddQuestionCommandHandler addQuestionHandler,
                                 DeleteQuestionCommandHandler deleteQuestionHandler,
                                 DeleteTagCommandHandler deleteTagHandler,
                                 RandomCommandHandler randomQuestionHandler,
                                 ScheduleCommandHandler scheduleCommandHandler,
                                 StartCommandHandler startCommandHandler,
                                 DeleteScheduleCommandHandler deleteScheduleCommandHandler
    ) {
        super(messageSender);
        this.sessionManager = sessionManager;
        this.addQuestionHandler = addQuestionHandler;
        this.deleteQuestionHandler = deleteQuestionHandler;
        this.randomQuestionHandler = randomQuestionHandler;
        this.deleteTagHandler = deleteTagHandler;
        this.scheduleCommandHandler = scheduleCommandHandler;
        this.startCommandHandler = startCommandHandler;
        this.deleteScheduleCommandHandler = deleteScheduleCommandHandler;
    }

    @Override
    public String getCommand() {
        return "/default";
    }

    @Override
    public void handle(Update update) {
        var userId = update.getMessage().getFrom().getId();
        Session session = sessionManager.getSession(userId);

        if(session == null) {
            messageSender.sendMessage(userId, "Неизвестная команда.\nВведите /help для просмотра команд");
            return;
        }

        if (session instanceof AddQuestionSession) {
            addQuestionHandler.handle(update);
        } else if (session instanceof QuizSession) {
            randomQuestionHandler.handle(update);
        } else if (session.getType() == SessionType.DELETE_CONFIRMATION) {
            handleDeleteConfirmation(update, userId);
        } else if (session.getType() == SessionType.DELETE_TAG_CONFIRMATION) {
            handleDeleteTagConfirmation(update, userId);
        } else if (session.getType() == SessionType.CREATING_SCHEDULE) {
            scheduleCommandHandler.handle(update);
        }else if (session.getType() == SessionType.WAITING_TIMEZONE) {
            startCommandHandler.handle(update);
        } else if (session.getType() == SessionType.DELETING_SCHEDULE) {
            deleteScheduleCommandHandler.handle(update);
            throw new RuntimeException("UNKNOWN STATE" + session.getType());
        }
    }

    private void handleDeleteConfirmation(Update update, Long userId) {
        String text = update.getMessage().getText().toLowerCase().trim();
        
        if (text.equals("да") || text.equals("yes") || text.equals("y")) {
            deleteQuestionHandler.confirmDeletion(userId, true);
            messageSender.sendMessage(update.getMessage().getChatId(), "✅ Вопрос удален.");
        } else if (text.equals("нет") || text.equals("no") || text.equals("n")) {
            deleteQuestionHandler.confirmDeletion(userId, false);
            messageSender.sendMessage(update.getMessage().getChatId(), "❗ Отменено.");
        } else {
            messageSender.sendMessage(update.getMessage().getChatId(),
                "Пожалуйста, ответьте «Да» или «Нет» для подтверждения удаления вопроса.");
        }
    }

    private void handleDeleteTagConfirmation(Update update, Long userId) {
        String text = update.getMessage().getText().toLowerCase().trim();
        
        if (text.equals("да") || text.equals("yes") || text.equals("y")) {
            deleteTagHandler.confirmDeletion(userId, true);
            messageSender.sendMessage(update.getMessage().getChatId(), "✅ Тег удален.");
        } else if (text.equals("нет") || text.equals("no") || text.equals("n")) {
            deleteTagHandler.confirmDeletion(userId, false);
            messageSender.sendMessage(update.getMessage().getChatId(), "❗ Отменено.");
        } else {
            messageSender.sendMessage(update.getMessage().getChatId(),
                "Пожалуйста, ответьте «Да» или «Нет» для подтверждения удаления тега.");
        }
    }
}

