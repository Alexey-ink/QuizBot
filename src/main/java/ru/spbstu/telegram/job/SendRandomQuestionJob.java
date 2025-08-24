package ru.spbstu.telegram.job;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;
import ru.spbstu.dto.QuizDto;
import ru.spbstu.service.quiz.QuizService;
import ru.spbstu.telegram.sender.MessageSender;

/**
 * Quartz Job, который отправляет случайный вопрос в чат.
 */
@Component
public class SendRandomQuestionJob implements Job {
    private final MessageSender messageSender;
    private final QuizService quizService;

    public SendRandomQuestionJob(MessageSender messageSender, QuizService quizService) {
        this.messageSender = messageSender;
        this.quizService = quizService;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        Long chatId = context.getMergedJobDataMap().getLong("chatId");
        Long userId = context.getMergedJobDataMap().getLong("userId");
        QuizDto quiz = quizService.getQuiz(userId);

        if (quiz == null) {
            messageSender.sendMessage(chatId,
                    "❌ В базе данных нет вопросов. Сначала добавьте несколько вопросов с помощью команды /add_question");
            return;
        }

        messageSender.sendPoll(chatId, quiz.question(), quiz.options(),
                quiz.correctOption(), "\uD83C\uDFB2");
    }
}
