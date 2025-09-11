package ru.spbstu.telegram.bot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.spbstu.service.UserService;
import ru.spbstu.telegram.handler.CommandHandler;
import ru.spbstu.telegram.session.QuizSession;
import ru.spbstu.telegram.utils.SessionManager;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class UpdateDispatcher {
    private final Map<String, CommandHandler> handlers;
    private final SessionManager sessionManager;
    private final UserService userService;

    @Autowired
    public UpdateDispatcher(List<CommandHandler> handlers, SessionManager sessionManager, UserService userService) {
        this.handlers = handlers.stream()
                .collect(Collectors.toMap(CommandHandler::getCommand, h -> h));
        this.sessionManager = sessionManager;
        this.userService = userService;
    }

    public void dispatch(Update update) {

        if (update.hasPollAnswer()) {
            handlePollAnswer(update);
        } else if (update.hasMessage() && update.getMessage().hasText()) {
            Long telegramId = update.getMessage().getFrom().getId();
            String username = update.getMessage().getFrom().getUserName();

            userService.getOrCreateUser(telegramId, username);

            String text = update.getMessage().getText();
            String command = text.split(" ")[0];

            if (sessionManager.hasSession(telegramId)) {
                handlers.get("/default").handle(update);
            } else {
                handlers.getOrDefault(command, handlers.get("/default")).handle(update);
            }
        }
    }

    private void handlePollAnswer(Update update) {
        var pollAnswer = update.getPollAnswer();
        var userId = pollAnswer.getUser().getId();

        var session = sessionManager.getSession(userId, QuizSession.class);
        if (session != null) {
            handlers.get("/random").handle(update);
        }
    }
}
