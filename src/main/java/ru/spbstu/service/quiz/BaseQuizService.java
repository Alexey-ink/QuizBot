package ru.spbstu.service.quiz;

import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.polls.SendPoll;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.polls.PollAnswer;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.spbstu.model.Question;
import ru.spbstu.model.QuestionOption;
import ru.spbstu.model.User;
import ru.spbstu.service.BaseService;
import ru.spbstu.service.ScoreByTagService;
import ru.spbstu.service.UserService;
import ru.spbstu.session.QuizSession;
import ru.spbstu.utils.SessionManager;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public abstract class BaseQuizService extends BaseService {

    protected final SessionManager sessionManager;
    private final UserService userService;
    private final ScoreByTagService scoreByTagService;

    protected BaseQuizService(SessionManager sessionManager, UserService userService, ScoreByTagService scoreByTagService) {
        this.sessionManager = sessionManager;
        this.userService = userService;
        this.scoreByTagService = scoreByTagService;
    }

    protected void createAndExecuteQuizPoll(Long chatId, Question question, AbsSender sender, String desc) {
        List<QuestionOption> sortedOptions = question.getOptions().stream()
                .sorted(Comparator.comparingInt(QuestionOption::getOptionNumber))
                .toList();

        List<String> options = sortedOptions.stream()
                .map(QuestionOption::getText)
                .collect(Collectors.toList());

        SendPoll poll = new SendPoll();
        poll.setChatId(chatId.toString());
        poll.setQuestion(desc + "\n" + question.getText());
        poll.setOptions(options);
        poll.setCorrectOptionId(question.getCorrectOption() - 1);
        poll.setType("quiz");
        poll.setOpenPeriod(30);
        poll.setIsAnonymous(false);
        try {
            sender.execute(poll);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    /**
     * Обрабатывает incoming PollAnswer (Update.hasPollAnswer()).
     */
    public void processPollAnswer(Update update, AbsSender sender) {

        PollAnswer pollAnswer = update.getPollAnswer();
        Long userId = pollAnswer.getUser().getId();
        var optionIds = pollAnswer.getOptionIds();

        if (optionIds == null || optionIds.isEmpty()) {
            return;
        }

        int selectedAnswer = optionIds.get(0) + 1; // обратно в 1-based

        QuizSession session = sessionManager.getSession(userId, QuizSession.class);
        if (session == null || session.isAnswered()) {
            return;
        }

        Question question = session.getCurrentQuestion();
        boolean isCorrect = selectedAnswer == question.getCorrectOption();

        if (isCorrect) {
            User user = userService.getUser(userId);
            user.setScore(user.getScore() + 1);
            userService.save(user);
            for (var tag : question.getTags()) {
                scoreByTagService.incrementScore(user, tag);
            }
        }

        User currentUser = userService.getUser(userId);
        sessionManager.clearSession(userId);
        showQuizResult(sender, userId, question, selectedAnswer, isCorrect, currentUser.getScore());
    }

    protected void showQuizResult(AbsSender sender, Long userId, Question question, int selectedAnswer, boolean isCorrect, int score) {
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

        sendMessage(sender, userId, message.toString());
    }
}
