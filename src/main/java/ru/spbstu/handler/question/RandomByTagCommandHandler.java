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
import ru.spbstu.service.ScoreByTagService;
import ru.spbstu.service.UserService;
import ru.spbstu.session.QuizSession;
import ru.spbstu.utils.SessionManager;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class RandomByTagCommandHandler implements CommandHandler {
    private final QuestionService questionService;
    private final SessionManager sessionManager;
    private final ScoreByTagService scoreByTagService;
    private final UserService userService;

    public RandomByTagCommandHandler(QuestionService questionService,
                                     SessionManager sessionManager,
                                     ScoreByTagService scoreByTagService,
                                     UserService userService) {
        this.questionService = questionService;
        this.sessionManager = sessionManager;
        this.scoreByTagService = scoreByTagService;
        this.userService = userService;
    }

    @Override
    public String getCommand() {
        return "/random_by_tag";
    }

    @Override
    public String getDescription() {
        return "Получить случайный вопрос по указанному тегу";
    }

    @Override
    public void handle(Update update, AbsSender sender) {
        var chatId = update.getMessage().getChatId();
        var userId = update.getMessage().getFrom().getId();
        var text = update.getMessage().getText();
        
        if (text.equals("/random_by_tag")) {
            send(sender, chatId, "📝 Использование: /random_by_tag <название_тега>\n\n" +
                    "Пример: /random_by_tag java");
            return;
        }
        
        if (text.startsWith("/random_by_tag ")) {
            String tagName = text.substring("/random_by_tag ".length()).trim();
            if (tagName.isEmpty()) {
                send(sender, chatId, "❌ Пожалуйста, укажите название тега.\n\n" +
                        "Пример: /random_by_tag java");
                return;
            }
            startNewQuizByTag(userId, chatId, tagName, sender);
            return;
        }

        System.out.println("Перед if update.hasPollAnswer()");
        if (update.hasPollAnswer()) {
            System.out.println("Зашли в if update.hasPollAnswer()");
            handlePollAnswer(update, sender);
        }
    }
    
    private void startNewQuizByTag(Long userId, Long chatId, String tagName, AbsSender sender) {
        Question randomQuestion = questionService.getRandomQuestionByTag(tagName);

        if (randomQuestion == null) {
            send(sender, chatId, "❌ Не найдено вопросов с тегом '" + tagName + "'.\n\n" +
                    "Убедитесь, что:\n" +
                    "• Тег существует\n" +
                    "• У вас есть вопросы с этим тегом\n" +
                    "• Используйте команду /list_tags для просмотра доступных тегов");
            return;
        }
        
        QuizSession session = sessionManager.getOrCreate(userId, QuizSession.class);
        session.setCurrentQuestion(randomQuestion);
        session.setStep(QuizSession.Step.WAITING_FOR_ANSWER);

        System.out.println("Waiting for question\n");
        
        List<QuestionOption> sortedOptions = randomQuestion.getOptions().stream()
                .sorted(Comparator.comparingInt(QuestionOption::getOptionNumber))
                .toList();
        
        List<String> options = sortedOptions.stream()
                .map(QuestionOption::getText)
                .collect(Collectors.toList());
        
        SendPoll poll = new SendPoll();
        poll.setChatId(chatId.toString());
        poll.setQuestion("🏷️ [" + tagName + "] " + randomQuestion.getText());
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
        System.out.println("ПЕРЕД SHOWQUIZ в randomByTag");
        showQuizResult(sender, userId, question, selectedAnswer, isCorrect, userService.getUser(userId).getScore());
    }
    
    private void showQuizResult(AbsSender sender, Long chatId, Question question,
                                int selectedAnswer, boolean isCorrect, int score) {
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
