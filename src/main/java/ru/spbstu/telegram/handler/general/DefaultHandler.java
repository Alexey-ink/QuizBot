package ru.spbstu.telegram.handler.general;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.spbstu.telegram.handler.CommandHandler;
import ru.spbstu.telegram.handler.question.AddQuestionCommandHandler;
import ru.spbstu.telegram.handler.question.DeleteQuestionCommandHandler;
import ru.spbstu.telegram.handler.quiz.RandomCommandHandler;
import ru.spbstu.telegram.handler.schedule.DeleteScheduleCommandHandler;
import ru.spbstu.telegram.handler.schedule.ScheduleCommandHandler;
import ru.spbstu.telegram.handler.tag.AddTagCommandHandler;
import ru.spbstu.telegram.handler.tag.DeleteTagCommandHandler;
import ru.spbstu.telegram.sender.MessageSender;
import ru.spbstu.telegram.session.core.Session;
import ru.spbstu.telegram.utils.SessionManager;
import ru.spbstu.telegram.session.core.SessionType;

import java.util.Map;

@Component
public class DefaultHandler extends CommandHandler {
    private final SessionManager sessionManager;
    private final Map<SessionType, CommandHandler> sessionHandlers;

    public DefaultHandler(MessageSender messageSender,
                          SessionManager sessionManager,
                          AddQuestionCommandHandler addQuestionHandler,
                          AddTagCommandHandler addTagCommandHandler,
                          DeleteQuestionCommandHandler deleteQuestionHandler,
                          DeleteTagCommandHandler deleteTagHandler,
                          RandomCommandHandler randomQuestionHandler,
                          ScheduleCommandHandler scheduleCommandHandler,
                          StartCommandHandler startCommandHandler,
                          DeleteScheduleCommandHandler deleteScheduleCommandHandler) {
        super(messageSender);
        this.sessionManager = sessionManager;

        this.sessionHandlers = Map.of(
                SessionType.QUESTION, addQuestionHandler,
                SessionType.ADDING_TAG, addTagCommandHandler,
                SessionType.QUIZ, randomQuestionHandler,
                SessionType.DELETE_CONFIRMATION, deleteQuestionHandler,
                SessionType.DELETE_TAG_CONFIRMATION, deleteTagHandler,
                SessionType.CREATING_SCHEDULE, scheduleCommandHandler,
                SessionType.WAITING_TIMEZONE, startCommandHandler,
                SessionType.DELETING_SCHEDULE, deleteScheduleCommandHandler
        );
    }

    @Override
    public String getCommand() {
        return "/default";
    }

    @Override
    public void handle(Update update) {
        var userId = update.getMessage().getFrom().getId();
        Session session = sessionManager.getSession(userId);

        if (session == null) {
            messageSender.sendMessage(userId,
                    "Неизвестная команда.\nВведите /help для просмотра команд");
            return;
        }

        CommandHandler handler = sessionHandlers.get(session.getType());
        if (handler != null) {
            handler.handle(update);
        } else {
            throw new IllegalStateException("Unknown session type: " + session.getType());
        }
    }
}

