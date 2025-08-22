package ru.spbstu.service.quiz;

import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.bots.AbsSender;
import ru.spbstu.model.Question;
import ru.spbstu.service.QuestionService;
import ru.spbstu.service.ScoreByTagService;
import ru.spbstu.service.UserService;
import ru.spbstu.session.QuizSession;
import ru.spbstu.utils.SessionManager;

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

    public void startNewQuizByTag(Long userId, Long chatId, String tagName, AbsSender sender) {
        Question randomQuestion = questionService.getRandomQuestionByTag(tagName);

        if (randomQuestion == null) {
            sendMessage(sender, chatId, "❌ Не найдено вопросов с тегом '" + tagName + "'.\n\n" +
                    "Убедитесь, что:\n" +
                    "• Тег существует\n" +
                    "• У вас есть вопросы с этим тегом\n" +
                    "• Используйте команду /list_tags для просмотра доступных тегов");
            return;
        }

        QuizSession session = sessionManager.getOrCreate(userId, QuizSession.class);
        session.setCurrentQuestion(randomQuestion);
        session.setStep(QuizSession.Step.WAITING_FOR_ANSWER);

        createAndExecuteQuizPoll(chatId, randomQuestion, sender, "\uD83C\uDFF7\uFE0F [" + tagName + "] ");

    }
}
