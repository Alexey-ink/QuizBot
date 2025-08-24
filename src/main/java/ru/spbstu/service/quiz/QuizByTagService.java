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

    public QuizDto getRandomQuizByTag(Long telegramId, String tagName) {
        Long userId = userService.getUserIdByTelegramIdOptional(telegramId).orElse(null);
        if(userId == null) throw new RuntimeException("NOT FOUND USER ID");
        Question randomQuestion = questionService.getRandomQuestionByTag(userId, tagName);

        if(randomQuestion == null) return null;

        List<String> options = questionService.getSortedOptions(randomQuestion);

        QuizDto quiz = new QuizDto(randomQuestion.getText(), options,
                randomQuestion.getCorrectOption() - 1);

        QuizSession session = sessionManager.getOrCreate(telegramId, QuizSession.class);
        session.setCurrentQuestion(randomQuestion);
        session.setStep(QuizSession.Step.WAITING_FOR_ANSWER);

        return quiz;
    }

    public boolean existsAnsweredByTag(Long userId, String tagName) {
        return questionService.existsAnsweredByTag(userId, tagName);
    }
}
