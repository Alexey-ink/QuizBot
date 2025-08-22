package ru.spbstu.handler.question;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.polls.SendPoll;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.spbstu.handler.CommandHandler;
import ru.spbstu.model.Question;
import ru.spbstu.model.QuestionOption;
import ru.spbstu.model.User;
import ru.spbstu.service.QuestionService;
import ru.spbstu.service.UserService;
import ru.spbstu.service.ScoreByTagService;
import ru.spbstu.session.QuizSession;
import ru.spbstu.utils.SessionManager;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class RandomQuestionCommandHandler implements CommandHandler {
    private final QuestionService questionService;
    private final SessionManager sessionManager;
    private final UserService userService;
    private final ScoreByTagService scoreByTagService;

    public RandomQuestionCommandHandler(QuestionService questionService, SessionManager sessionManager, UserService userService, ScoreByTagService scoreByTagService) {
        this.questionService = questionService;
        this.sessionManager = sessionManager;
        this.userService = userService;
        this.scoreByTagService = scoreByTagService;
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
            handlePollAnswer(update, sender);
            return;
        }
        var chatId = update.getMessage().getChatId();
        var userId = update.getMessage().getFrom().getId();
        var text = update.getMessage().getText();
        
        if (text.equals("/random")) {
            startNewQuiz(userId, chatId, sender);
            return;
        }
    }
    
    private void startNewQuiz(Long userId, Long chatId, AbsSender sender) {
        Question randomQuestion = questionService.getRandomQuestion();
        
        if (randomQuestion == null) {
            sendMessage(sender, chatId, "❌ В базе данных нет вопросов. Сначала добавьте несколько вопросов с помощью команды /add_question");
            return;
        }
        
        // Создаем новую сессию викторины
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
        poll.setCorrectOptionId(randomQuestion.getCorrectOption() - 1); // Telegram использует 0-based индексы
        poll.setType("quiz");
        poll.setOpenPeriod(30);
        poll.setIsAnonymous(false);
        
        try {
            sender.execute(poll);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
    
    private void handlePollAnswer(Update update, AbsSender sender) {
        var pollAnswer = update.getPollAnswer();
        var userId = pollAnswer.getUser().getId();
        var optionIds = pollAnswer.getOptionIds();
        
        if (optionIds == null || optionIds.isEmpty()) {
            return;
        }
        
        int selectedAnswer = optionIds.get(0) + 1; // Конвертируем обратно в 1-based
        
        QuizSession session = sessionManager.getSession(userId, QuizSession.class);
        if (session == null || session.isAnswered()) {
            return;
        }
        
        session.setAnswered(true);
        
        Question question = session.getCurrentQuestion();
        boolean isCorrect = selectedAnswer == question.getCorrectOption();
        
        if (isCorrect) {
            var user = userService.getUser(userId);
            user.setScore(user.getScore() + 1);
            userService.save(user);
            for (var tag : question.getTags()) {
                scoreByTagService.incrementScore(user, tag);
            }
        }
        sessionManager.clearSession(userId);
        showQuizResult(sender, userId, question, selectedAnswer, isCorrect, userService.getUser(userId).getScore());
    }
    
    private void showQuizResult(AbsSender sender, Long userId, Question question, int selectedAnswer, boolean isCorrect, int score) {
        StringBuilder message = new StringBuilder();

        if (isCorrect) {
            message.append("✅ **Правильно!** +1 балл\n\n");
        } else {
            message.append("❌ **Неверно!**\n\n");
        }
        
        QuestionOption correctOption = question.getOptions().stream()
                .filter(option -> option.getOptionNumber() == question.getCorrectOption())
                .findFirst()
                .orElse(null);
        
        if (correctOption != null && !isCorrect) {
            message.append("💡 **Правильный ответ:** ").append(question.getCorrectOption())
                   .append(". ").append(correctOption.getText()).append("\n\n");
        }
        
        // Добавляем информацию о тегах, если они есть
        if (!question.getTags().isEmpty()) {
            String tags = question.getTags().stream()
                    .map(tag -> "#" + tag.getName())
                    .collect(Collectors.joining(" "));
            message.append("🏷️ **Теги:** ").append(tags).append("\n\n");
        }
        
        message.append("🏆 **Ваш счет:** ").append(score).append(" баллов");
        sendMessage(sender, userId, message.toString());

    }
}

