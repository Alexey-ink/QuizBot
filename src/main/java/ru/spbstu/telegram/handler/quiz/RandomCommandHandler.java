package ru.spbstu.telegram.handler.quiz;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.polls.PollAnswer;
import ru.spbstu.dto.QuizDto;
import ru.spbstu.telegram.handler.CommandHandler;
import ru.spbstu.service.quiz.QuizService;
import ru.spbstu.telegram.sender.MessageSender;

import java.util.List;

@Component
public class RandomCommandHandler extends CommandHandler {
    private final QuizService quizService;

    public RandomCommandHandler(MessageSender messageSender,
                                QuizService quizService) {
        super(messageSender);
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
    public void handle(Update update) {
        if (update.hasPollAnswer()) {
            PollAnswer pollAnswer = update.getPollAnswer();
            List<Integer> optionIds = pollAnswer.getOptionIds();
            Long telegramId = update.getPollAnswer().getUser().getId();

            int selectedAnswer = optionIds.getFirst() + 1;

            messageSender.sendMessage(telegramId,
                    quizService.processPollAnswer(telegramId, selectedAnswer)
            );
            return;
        }

        Long userId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();
        QuizDto quiz = quizService.getQuiz(userId);

        if (quiz == null) {
            messageSender.sendMessage(chatId,
                    "❌ В базе данных нет вопросов. Сначала добавьте несколько вопросов с помощью команды /add_question");
            return;
        }

        messageSender.sendPoll(chatId, quiz.question(), quiz.options(),
                quiz.correctOption(), "\uD83C\uDFB2");

    }
}

