package ru.spbstu.service.quiz;

import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.bots.AbsSender;
import ru.spbstu.model.Question;
import ru.spbstu.service.QuestionService;
import ru.spbstu.service.ScoreByTagService;
import ru.spbstu.service.UserService;
import ru.spbstu.session.QuizSession;
import ru.spbstu.utils.SessionManager;

@Service
public class QuizService extends BaseQuizService {

    private final QuestionService questionService;

    public QuizService(QuestionService questionService,
                       UserService userService,
                       ScoreByTagService scoreByTagService,
                       SessionManager sessionManager) {
        super(sessionManager, userService, scoreByTagService);
        this.questionService = questionService;
    }

    /**
     * Запускает новую викторину: берёт случайный вопрос, создаёт/обновляет сессию
     * и отправляет SendPoll в чат.
     */
    public void startNewQuiz(Long userId, Long chatId, AbsSender sender) {
        Question randomQuestion = questionService.getRandomQuestion();

        if (randomQuestion == null) {
            sendMessage(sender, chatId, "❌ В базе данных нет вопросов. Сначала добавьте несколько вопросов с помощью команды /add_question");
            return;
        }

        QuizSession session = sessionManager.getOrCreate(userId, QuizSession.class);
        session.setCurrentQuestion(randomQuestion);
        session.setStep(QuizSession.Step.WAITING_FOR_ANSWER);
        createAndExecuteQuizPoll(chatId, randomQuestion, sender, "\uD83C\uDFB2");
    }
}
