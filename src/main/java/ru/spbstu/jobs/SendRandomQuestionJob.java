package ru.spbstu.jobs;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.bots.AbsSender;
import ru.spbstu.service.quiz.QuizService;

/**
 * Quartz Job, который отправляет случайный вопрос в чат.
 */
@Component
public class SendRandomQuestionJob implements Job {

    private final QuizService quizService;
    private final AbsSender sender;

    public SendRandomQuestionJob(QuizService quizService, AbsSender sender) {
        this.quizService = quizService;
        this.sender = sender;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        Long chatId = context.getMergedJobDataMap().getLong("chatId");
        Long userId = context.getMergedJobDataMap().getLong("userId");
        quizService.startNewQuiz(userId, chatId, sender);
    }
}
