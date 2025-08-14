package ru.spbstu.handler;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.spbstu.session.Session;
import ru.spbstu.utils.SessionManager;
import ru.spbstu.session.QuestionSession;
import ru.spbstu.session.QuizSession;

@Component
public class SessionMessageHandler implements CommandHandler {
    private final SessionManager sessionManager;
    private final AddQuestionCommandHandler addQuestionHandler;
    private final RandomQuestionCommandHandler randomQuestionHandler;

    public SessionMessageHandler(SessionManager sessionManager, 
                               AddQuestionCommandHandler addQuestionHandler,
                               RandomQuestionCommandHandler randomQuestionHandler) {
        this.sessionManager = sessionManager;
        this.addQuestionHandler = addQuestionHandler;
        this.randomQuestionHandler = randomQuestionHandler;
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
        else if (session instanceof QuizSession) {
            randomQuestionHandler.handle(update, sender);
        }
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
}