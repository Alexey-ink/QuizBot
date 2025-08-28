package ru.spbstu.telegram.handler.quiz;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.polls.PollAnswer;
import ru.spbstu.telegram.handler.CommandHandler;
import ru.spbstu.telegram.sender.MessageSender;
import ru.spbstu.service.quiz.QuizService;
import ru.spbstu.dto.QuizDto;

import java.util.List;

@Component
public class RandomCommandHandler extends CommandHandler {
    private final QuizService quizService;

    public RandomCommandHandler(MessageSender messageSender,
                                QuizService quizService) {
        super(messageSender);
        this.quizService = quizService;
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
    public void handle(Update update) {
        if (update.hasPollAnswer()) {
            PollAnswer pollAnswer = update.getPollAnswer();
            List<Integer> optionIds = pollAnswer.getOptionIds();
            Long telegramId = update.getPollAnswer().getUser().getId();

            logger.info("Обработка ответа на вопрос {} от пользователя {}",
                    pollAnswer.getPollId(), telegramId);
            try {
                int selectedAnswer = optionIds.getFirst() + 1;

                messageSender.sendMessage(telegramId,
                        quizService.processPollAnswer(telegramId, selectedAnswer)
                );
            } catch (Exception e) {
            logger.error("Ошибка при обработке ответа на опрос {} пользователем {}: {}",
                    pollAnswer.getPollId(), telegramId, e.getMessage(), e);
            messageSender.sendMessage(telegramId, "❌ Произошла ошибка при обработке ответа");
        }
        return;
        }

        Long telegramId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();
        QuizDto quiz = quizService.getQuiz(telegramId);

        try {
            if (quiz == null) {
                messageSender.sendMessage(chatId,
                        "❌ В базе данных нет вопросов. Сначала добавьте несколько вопросов с помощью команды /add_question");
                return;
            }

            logger.debug("Отправка случайного вопроса пользователю {}", telegramId);
            messageSender.sendPoll(chatId, quiz.question(), quiz.options(),
                    quiz.correctOption(), "\uD83C\uDFB2");

        } catch (Exception e) {
            logger.error("Ошибка при обработке команды /random пользователем {}: {}", telegramId, e.getMessage(), e);
            messageSender.sendMessage(chatId, "❌ Произошла ошибка при получении вопроса");
        }
    }
}

