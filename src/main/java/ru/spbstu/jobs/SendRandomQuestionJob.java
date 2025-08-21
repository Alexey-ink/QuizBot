package ru.spbstu.jobs;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.spbstu.model.Question;
import ru.spbstu.repository.QuestionRepository;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.bots.AbsSender;
import ru.spbstu.service.QuizService;

import java.util.Optional;

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
        // Достаём данные, которые мы положили в JobDataMap в ScheduleService
        Long scheduleId = context.getMergedJobDataMap().getLong("scheduleId");
        Long chatId = context.getMergedJobDataMap().getLong("chatId");
        Long userId = context.getMergedJobDataMap().getLong("user_id");
        quizService.startNewQuiz(userId, chatId, sender);
    }
}
