package ru.spbstu.service.quiz;

import org.springframework.stereotype.Service;
import ru.spbstu.model.Question;
import ru.spbstu.model.QuestionOption;
import ru.spbstu.model.User;
import ru.spbstu.service.ScoreByTagService;
import ru.spbstu.service.UserService;
import ru.spbstu.telegram.session.QuizSession;
import ru.spbstu.telegram.utils.SessionManager;

import java.util.stream.Collectors;

@Service
public abstract class BaseQuizService {

    protected final SessionManager sessionManager;
    private final UserService userService;
    private final ScoreByTagService scoreByTagService;

    protected BaseQuizService(SessionManager sessionManager,
                              UserService userService,
                              ScoreByTagService scoreByTagService) {
        this.sessionManager = sessionManager;
        this.userService = userService;
        this.scoreByTagService = scoreByTagService;
    }

    /**
     * Обрабатывает incoming PollAnswer (Update.hasPollAnswer()).
     */
    public String processPollAnswer(Long telegramId, int selectedAnswer) {

        QuizSession session = sessionManager.getSession(telegramId, QuizSession.class);
        if (session == null || session.isAnswered()) {
            return null;
        }

        Question question = session.getCurrentQuestion();
        boolean isCorrect = selectedAnswer == question.getCorrectOption();

        User user = userService.getUser(telegramId);

        if (isCorrect) {
            user.setScore(user.getScore() + 1);
            userService.save(user);
            for (var tag : question.getTags()) {
                scoreByTagService.incrementScore(user, tag);
            }
        }
        sessionManager.clearSession(telegramId);
        return getQuizResult(telegramId, question, isCorrect, user.getScore());
    }

    protected String getQuizResult(Long userId, Question question, boolean isCorrect, int score) {
        StringBuilder message = new StringBuilder();

        if (isCorrect) {
            message.append("✅ *Правильно!* +1 балл\n\n");
        } else {
            message.append("❌ *Неверно!*\n\n");
        }

        QuestionOption correctOption = question.getOptions().stream()
                .filter(option -> option.getOptionNumber() == question.getCorrectOption())
                .findFirst()
                .orElse(null);

        if (correctOption != null && !isCorrect) {
            message.append("💡 *Правильный ответ:* ").append(question.getCorrectOption())
                    .append(". ").append(correctOption.getText()).append("\n\n");
        }

        if (!question.getTags().isEmpty()) {
            String tags = question.getTags().stream()
                    .map(tag -> "#" + tag.getName())
                    .collect(Collectors.joining(" "));
            message.append("🏷️ *Теги:* ").append(tags).append("\n\n");
        }

        message.append("🏆 *Ваш счет:* ").append(score).append(" баллов");

        return message.toString();
    }
}
