package ru.spbstu.service.quiz;

import org.springframework.stereotype.Service;
import ru.spbstu.dto.QuizDto;
import ru.spbstu.model.Question;
import ru.spbstu.service.QuestionService;
import ru.spbstu.service.ScoreByTagService;
import ru.spbstu.service.UserService;
import ru.spbstu.telegram.session.QuizSession;
import ru.spbstu.telegram.utils.SessionManager;

import java.util.List;

@Service
public class QuizByTagService extends BaseQuizService {
    private final QuestionService questionService;

    public QuizByTagService(QuestionService questionService,
                       UserService userService,
                       ScoreByTagService scoreByTagService,
                       SessionManager sessionManager) {
        super(sessionManager, userService, scoreByTagService);
        this.questionService = questionService;
    }

    public QuizDto getRandomQuizByTag(Long userId, String tagName) {
        Question randomQuestion = questionService.getRandomQuestionByTag(tagName);

        if(randomQuestion == null) return null;

        List<String> options = questionService.getSortedOptions(randomQuestion);

        QuizDto quiz = new QuizDto(randomQuestion.getText(), options,
                randomQuestion.getCorrectOption() - 1);

        QuizSession session = sessionManager.getOrCreate(userId, QuizSession.class);
        session.setCurrentQuestion(randomQuestion);
        session.setStep(QuizSession.Step.WAITING_FOR_ANSWER);

        return quiz;
    }
}
