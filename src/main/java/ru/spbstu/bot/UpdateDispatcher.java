package ru.spbstu.bot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.bots.AbsSender;
import ru.spbstu.handler.CommandHandler;
import ru.spbstu.session.QuizSession;
import ru.spbstu.utils.SessionManager;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class UpdateDispatcher {
    private final Map<String, CommandHandler> handlers;
    private final SessionManager sessionManager;

    @Autowired
    public UpdateDispatcher(List<CommandHandler> handlers, SessionManager sessionManager) {
        this.handlers = handlers.stream()
                .collect(Collectors.toMap(CommandHandler::getCommand, h -> h));
        this.sessionManager = sessionManager;
    }

    public void dispatch(Update update, AbsSender sender) {
        if (update.hasPollAnswer()) {
            System.out.println("Check our QUIZ session: " + sessionManager.getSession(1163706093L));
            handlePollAnswer(update, sender);
        } else if (update.hasMessage() && update.getMessage().hasText()) {
            String text = update.getMessage().getText();
            String command = text.split(" ")[0];
            handlers.getOrDefault(command, handlers.get("/default"))
                    .handle(update, sender);
        }
    }
    
    private void handlePollAnswer(Update update, AbsSender sender) {
        var pollAnswer = update.getPollAnswer();
        var userId = pollAnswer.getUser().getId();
        
        // Проверяем, есть ли активная сессия викторины у пользователя
        var session = sessionManager.getSession(userId, QuizSession.class);
        if (session != null) {
            // Находим обработчик для /random или /random_by_tag и передаем ему poll answer
            handlers.get("/random").handle(update, sender);
        }
    }
}
