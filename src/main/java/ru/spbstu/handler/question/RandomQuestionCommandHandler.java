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
import ru.spbstu.service.QuestionService;
import ru.spbstu.session.QuizSession;
import ru.spbstu.utils.SessionManager;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class RandomQuestionCommandHandler implements CommandHandler {
    private final QuestionService questionService;
    private final SessionManager sessionManager;

    public RandomQuestionCommandHandler(QuestionService questionService, SessionManager sessionManager) {
        this.questionService = questionService;
        this.sessionManager = sessionManager;
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
        var chatId = update.getMessage().getChatId();
        var userId = update.getMessage().getFrom().getId();
        var text = update.getMessage().getText();
        
        // Если это команда /random - начинаем новую викторину
        if (text.equals("/random")) {
            startNewQuiz(userId, chatId, sender);
            return;
        }
        
        // Если это ответ на опрос
        if (update.hasPollAnswer()) {
            handlePollAnswer(update, sender);
            return;
        }
        
        // Если это обычное сообщение во время викторины
        QuizSession session = sessionManager.getSession(userId, QuizSession.class);
        if (session != null && !session.isAnswered()) {
            handleTextAnswer(update, sender);
        }
    }
    
    private void startNewQuiz(Long userId, Long chatId, AbsSender sender) {
        Question randomQuestion = questionService.getRandomQuestion();
        
        if (randomQuestion == null) {
            send(sender, chatId, "❌ В базе данных нет вопросов. Сначала добавьте несколько вопросов с помощью команды /add_question");
            return;
        }
        
        // Создаем новую сессию викторины
        QuizSession session = sessionManager.getOrCreate(userId, QuizSession.class);
        session.setCurrentQuestion(randomQuestion);
        session.setStep(QuizSession.Step.WAITING_FOR_ANSWER);
        
        // Получаем варианты ответов и сортируем их по номеру
        List<QuestionOption> sortedOptions = randomQuestion.getOptions().stream()
                .sorted((o1, o2) -> Integer.compare(o1.getOptionNumber(), o2.getOptionNumber()))
                .collect(Collectors.toList());
        
        // Создаем список вариантов ответов для опроса
        List<String> options = sortedOptions.stream()
                .map(QuestionOption::getText)
                .collect(Collectors.toList());
        
        // Создаем опрос
        SendPoll poll = new SendPoll();
        poll.setChatId(chatId.toString());
        poll.setQuestion("🎲 " + randomQuestion.getText());
        poll.setOptions(options);
        poll.setCorrectOptionId(randomQuestion.getCorrectOption() - 1); // Telegram использует 0-based индексы
        poll.setType("quiz");
        poll.setExplanation("💡 Правильный ответ будет показан после завершения опроса");
        poll.setOpenPeriod(30); // 30 секунд на ответ
        poll.setIsAnonymous(true);
        
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
            session.incrementScore();
        }
        
        // Показываем результат
        showQuizResult(sender, userId, question, selectedAnswer, isCorrect, session.getScore());
    }
    
    private void handleTextAnswer(Update update, AbsSender sender) {
        var chatId = update.getMessage().getChatId();
        var userId = update.getMessage().getFrom().getId();
        var text = update.getMessage().getText();
        
        QuizSession session = sessionManager.getSession(userId, QuizSession.class);
        if (session == null || session.isAnswered()) {
            return;
        }
        
        // Проверяем, не истекло ли время
        if (session.isTimeExpired()) {
            session.setAnswered(true);
            send(sender, chatId, "⏰ Время истекло!");
            showCorrectAnswer(sender, chatId, session.getCurrentQuestion());
            return;
        }
        
        // Пытаемся распарсить номер ответа
        try {
            int selectedAnswer = Integer.parseInt(text.trim());
            if (selectedAnswer < 1 || selectedAnswer > 4) {
                send(sender, chatId, "❌ Введите число от 1 до 4 или используйте опрос.");
                return;
            }
            
            session.setAnswered(true);
            
            Question question = session.getCurrentQuestion();
            boolean isCorrect = selectedAnswer == question.getCorrectOption();
            
            if (isCorrect) {
                session.incrementScore();
            }
            
            // Показываем результат
            showQuizResult(sender, chatId, question, selectedAnswer, isCorrect, session.getScore());
            
        } catch (NumberFormatException e) {
            send(sender, chatId, "❌ Введите число от 1 до 4 или используйте опрос.");
        }
    }
    
    private void showQuizResult(AbsSender sender, Long chatId, Question question, int selectedAnswer, boolean isCorrect, int score) {
        StringBuilder message = new StringBuilder();
        
        if (isCorrect) {
            message.append("✅ <b>Правильно!</b> +1 балл\n\n");
        } else {
            message.append("❌ <b>Неверно!</b>\n\n");
        }
        
        // Показываем правильный ответ
        QuestionOption correctOption = question.getOptions().stream()
                .filter(option -> option.getOptionNumber() == question.getCorrectOption())
                .findFirst()
                .orElse(null);
        
        if (correctOption != null) {
            message.append("💡 <b>Правильный ответ:</b> ").append(question.getCorrectOption())
                   .append(". ").append(correctOption.getText()).append("\n\n");
        }
        
        // Добавляем информацию о тегах, если они есть
        if (!question.getTags().isEmpty()) {
            String tags = question.getTags().stream()
                    .map(tag -> "#" + tag.getName())
                    .collect(Collectors.joining(" "));
            message.append("🏷️ <b>Теги:</b> ").append(tags).append("\n\n");
        }
        
        // Показываем счет
        message.append("🏆 <b>Ваш счет:</b> ").append(score).append(" баллов");
        
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId.toString());
        sendMessage.setText(message.toString());
        sendMessage.setParseMode("HTML");
        
        try {
            sender.execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
    
    private void showCorrectAnswer(AbsSender sender, Long chatId, Question question) {
        // Получаем правильный вариант ответа
        QuestionOption correctOption = question.getOptions().stream()
                .filter(option -> option.getOptionNumber() == question.getCorrectOption())
                .findFirst()
                .orElse(null);
        
        if (correctOption != null) {
            StringBuilder message = new StringBuilder();
            message.append("💡 <b>Правильный ответ:</b> ").append(question.getCorrectOption())
                   .append(". ").append(correctOption.getText()).append("\n\n");
            
            // Добавляем информацию о тегах, если они есть
            if (!question.getTags().isEmpty()) {
                String tags = question.getTags().stream()
                        .map(tag -> "#" + tag.getName())
                        .collect(Collectors.joining(" "));
                message.append("🏷️ <b>Теги:</b> ").append(tags);
            }
            
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId.toString());
            sendMessage.setText(message.toString());
            sendMessage.setParseMode("HTML");
            
            try {
                sender.execute(sendMessage);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }

    private void send(AbsSender sender, Long chatId, String text) {
        try {
            sender.execute(new SendMessage(chatId.toString(), text));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

