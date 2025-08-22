package ru.spbstu.service;

import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.polls.SendPoll;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.polls.PollAnswer;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.spbstu.model.Question;
import ru.spbstu.model.QuestionOption;
import ru.spbstu.model.User;
import ru.spbstu.session.QuizSession;
import ru.spbstu.utils.SessionManager;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class QuizService {

    private final SessionManager sessionManager;
    private final QuestionService questionService;
    private final UserService userService;
    private final ScoreByTagService scoreByTagService;

    public QuizService(SessionManager sessionManager,
                       QuestionService questionService,
                       UserService userService,
                       ScoreByTagService scoreByTagService) {
        this.sessionManager = sessionManager;
        this.questionService = questionService;
        this.userService = userService;
        this.scoreByTagService = scoreByTagService;
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

        List<QuestionOption> sortedOptions = randomQuestion.getOptions().stream()
                .sorted(Comparator.comparingInt(QuestionOption::getOptionNumber))
                .toList();

        List<String> options = sortedOptions.stream()
                .map(QuestionOption::getText)
                .collect(Collectors.toList());

        SendPoll poll = new SendPoll();
        poll.setChatId(chatId.toString());
        poll.setQuestion("🎲 " + randomQuestion.getText());
        poll.setOptions(options);
        poll.setCorrectOptionId(randomQuestion.getCorrectOption() - 1);
        poll.setType("quiz");
        poll.setOpenPeriod(30);
        poll.setIsAnonymous(false);

        System.out.println("Check our QUIZ session: " + sessionManager.getSession(userId));

        try {
            sender.execute(poll);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    /**
     * Обрабатывает incoming PollAnswer (Update.hasPollAnswer()).
     * Делегируйте вызов из хэндлера: quizService.processPollAnswer(update, sender)
     */
    public void processPollAnswer(Update update, AbsSender sender) {
        if (!update.hasPollAnswer()) return;

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

        session.setAnswered(true);

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

        User currentUser = userService.getUser(userId); // чтобы получить актуальный счёт
        showQuizResult(sender, userId, question, selectedAnswer, isCorrect, currentUser.getScore());
    }

    private void showQuizResult(AbsSender sender, Long userId, Question question, int selectedAnswer, boolean isCorrect, int score) {
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

    private void sendMessage(AbsSender sender, Long chatId, String text) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText(text);
        msg.setParseMode("Markdown"); // используемые звёздочки/курсивы; можно сменить при необходимости

        try {
            sender.execute(msg);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
