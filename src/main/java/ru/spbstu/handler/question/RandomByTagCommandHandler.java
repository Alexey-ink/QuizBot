package ru.spbstu.handler.question;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.polls.SendPoll;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.spbstu.handler.CommandHandler;
import ru.spbstu.model.Question;
import ru.spbstu.model.QuestionOption;
import ru.spbstu.service.QuestionService;
import ru.spbstu.service.ScoreByTagService;
import ru.spbstu.service.UserService;
import ru.spbstu.service.quiz.QuizByTagService;
import ru.spbstu.session.QuizSession;
import ru.spbstu.utils.SessionManager;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class RandomByTagCommandHandler implements CommandHandler {
    private final QuizByTagService quizByTagService;

    public RandomByTagCommandHandler(QuizByTagService quizByTagService) {
        this.quizByTagService = quizByTagService;
    }

    @Override
    public String getCommand() {
        return "/random_by_tag";
    }

    @Override
    public String getDescription() {
        return "Получить случайный вопрос по указанному тегу";
    }

    @Override
    public void handle(Update update, AbsSender sender) {
        if (update.hasPollAnswer()) {
            quizByTagService.processPollAnswer(update, sender);
            return;
        }

        var chatId = update.getMessage().getChatId();
        var userId = update.getMessage().getFrom().getId();
        String text = update.getMessage().getText();

        if (text.equals("/random_by_tag")) {
            sendMessage(sender, chatId, "❌ Пожалуйста, укажите название тега.\n\n" +
                    "Пример: `/random_by_tag java`");
            return;
        }

        String[] parts = text.split(" ");

        if(parts.length > 2) {
            sendMessage(sender, update.getMessage().getChatId(),
                    "❌ Укажите один тег без пробелов.\nИспользование: `/random_by_tag <тег>`");
            return;
        }
        quizByTagService.startNewQuizByTag(userId, chatId, parts[1], sender);
    }
}
