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
import java.util.Optional;

@Service
public class QuizByTagService extends BaseQuizService {
    private final QuestionService questionService;

    public QuizByTagService(UserQuestionRepository userQuestionRepository,
                            QuestionService questionService,
                            UserService userService,
                            ScoreByTagService scoreByTagService,
                            SessionManager sessionManager) {
        super(sessionManager, userService, scoreByTagService, userQuestionRepository);
        this.questionService = questionService;
    }

    public Optional<QuizDto> getRandomQuizByTag(Long telegramId, String tagName) {
        Long userId = userService.getUserIdByTelegramId(telegramId);

        Optional<Question> randomQuestion = questionService.getRandomQuestionByTag(userId, tagName);

        if (randomQuestion.isEmpty()) {
            return Optional.empty();
        }

        QuizDto quiz = createQuizDto(randomQuestion.get());

        QuizSession session = sessionManager.getOrCreate(telegramId, QuizSession.class);
        session.setCurrentQuestion(randomQuestion.get());
        session.setStep(QuizSession.Step.WAITING_FOR_ANSWER);

        return Optional.of(quiz);
    }

    private QuizDto createQuizDto(Question question) {
        List<String> options = questionService.getSortedOptions(question);
        return new QuizDto(
                question.getText(),
                options,
                question.getCorrectOption() - 1
        );
    }

    public boolean existsAnsweredByTag(Long userId, String tagName) {
        return questionService.existsAnsweredByTag(userId, tagName);
    }
}
