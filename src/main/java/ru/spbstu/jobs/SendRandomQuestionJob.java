package ru.spbstu.jobs;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;
import ru.spbstu.model.Question;
import ru.spbstu.repository.QuestionRepository;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.bots.AbsSender;

import java.util.Optional;

/**
 * Quartz Job, который отправляет случайный вопрос в чат.
 */
@Component
public class SendRandomQuestionJob implements Job {

    private final QuestionRepository questionRepository;
    private final AbsSender sender;

    // Внедрение зависимостей через конструктор (Spring создаёт бин)
    public SendRandomQuestionJob(QuestionRepository questionRepository, AbsSender sender) {
        this.questionRepository = questionRepository;
        this.sender = sender;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        // Достаём данные, которые мы положили в JobDataMap в ScheduleService
        Long scheduleId = context.getMergedJobDataMap().getLong("scheduleId");
        Long chatId = context.getMergedJobDataMap().getLong("chatId");

        // Берём случайный вопрос (пример: у тебя в репозитории уже был метод findRandomQuestionByTag)
        Optional<Question> randomQuestion = questionRepository.findRandomQuestion();

        if (randomQuestion.isPresent()) {
            Question q = randomQuestion.get();
            SendMessage msg = new SendMessage(chatId.toString(), "❓ " + q.getText());
            try {
                sender.execute(msg);
            } catch (Exception e) {
                throw new JobExecutionException("Ошибка при отправке сообщения в чат " + chatId, e);
            }
        }
    }
}
