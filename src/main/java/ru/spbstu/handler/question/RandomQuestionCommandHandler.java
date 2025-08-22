package ru.spbstu.handler.question;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.bots.AbsSender;
import ru.spbstu.handler.CommandHandler;
import ru.spbstu.service.quiz.QuizService;

@Component
public class RandomQuestionCommandHandler implements CommandHandler {
    private final QuizService quizService;

    public RandomQuestionCommandHandler(QuizService quizService) {
        this.quizService = quizService;
    }

    @Override
    public String getCommand() {
        return "/random";
    }

    @Override
    public String getDescription() {
        return "Получить случайный вопрос для викторины";
    }

    @Override
    public void handle(Update update, AbsSender sender) {
        if (update.hasPollAnswer()) {
            quizService.processPollAnswer(update, sender);
            return;
        }
        Long chatId = update.getMessage().getChatId();
        Long userId = update.getMessage().getFrom().getId();
        quizService.startNewQuiz(userId, chatId, sender);
    }
}

