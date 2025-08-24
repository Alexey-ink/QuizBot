package ru.spbstu.service.quiz;

import org.springframework.stereotype.Service;
import ru.spbstu.dto.QuizDto;
import ru.spbstu.model.Question;
import ru.spbstu.repository.UserQuestionRepository;
import ru.spbstu.service.QuestionService;
import ru.spbstu.service.ScoreByTagService;
import ru.spbstu.service.UserService;
import ru.spbstu.telegram.session.QuizSession;
import ru.spbstu.telegram.utils.SessionManager;

import java.util.List;

@Service
public class QuizService extends BaseQuizService {

    private final QuestionService questionService;

    public QuizService(UserQuestionRepository userQuestionRepository,
                       QuestionService questionService,
                       UserService userService,
                       ScoreByTagService scoreByTagService,
                       SessionManager sessionManager) {
        super(sessionManager, userService, scoreByTagService, userQuestionRepository);
        this.questionService = questionService;
    }

    public QuizDto getQuiz(Long telegramId) {
        Long userId = userService.getUserIdByTelegramIdOptional(telegramId);
        Question randomQuestion = questionService.getRandomQuestion(userId);

        if (randomQuestion == null) return null;

        List<String> options = questionService.getSortedOptions(randomQuestion);

        QuizDto quiz = new QuizDto(randomQuestion.getText(), options,
                randomQuestion.getCorrectOption() - 1);

        QuizSession session = sessionManager.getOrCreate(telegramId, QuizSession.class);
        session.setCurrentQuestion(randomQuestion);
        session.setStep(QuizSession.Step.WAITING_FOR_ANSWER);
        return quiz;
    }
}
