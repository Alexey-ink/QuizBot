package ru.spbstu.telegram.handler.quiz;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.polls.PollAnswer;
import ru.spbstu.telegram.handler.CommandHandler;
import ru.spbstu.telegram.sender.MessageSender;
import ru.spbstu.service.quiz.QuizByTagService;
import ru.spbstu.dto.QuizDto;
import java.util.List;

@Component
public class RandomByTagCommandHandler extends CommandHandler {
    private final QuizByTagService quizByTagService;

    public RandomByTagCommandHandler(MessageSender messageSender,
                                     QuizByTagService quizByTagService) {
        super(messageSender);
        this.quizByTagService = quizByTagService;
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
    public void handle(Update update) {
        if (update.hasPollAnswer()) {
            PollAnswer pollAnswer = update.getPollAnswer();
            List<Integer> optionIds = pollAnswer.getOptionIds();
            Long telegramId = update.getPollAnswer().getUser().getId();
            int selectedAnswer = optionIds.getFirst() + 1;

            messageSender.sendMessage(telegramId,
                    quizByTagService.processPollAnswer(telegramId, selectedAnswer)
            );
        }

        String RANDOM_BY_TAG_COMMAND = "/random_by_tag";

        Long chatId = update.getMessage().getChatId();
        Long telegramId = update.getMessage().getFrom().getId();
        String text = update.getMessage().getText();

        if (text.equals(RANDOM_BY_TAG_COMMAND)) {
            messageSender.sendMessage(chatId, "❌ Пожалуйста, укажите название тега.\n\n" +
                    "Пример: `" + RANDOM_BY_TAG_COMMAND + " java`");
            return;
        }

        String[] parts = text.split(" ");

        if(parts.length > 2) {
            messageSender.sendMessage(update.getMessage().getChatId(),
                    "❌ Укажите один тег без пробелов.\nИспользование: `/random_by_tag <тег>`");
            return;
        }

        String tagName = parts[1];
        QuizDto quiz = quizByTagService.getRandomQuizByTag(telegramId, tagName);

        if (quiz == null) {
            if (quizByTagService.existsAnsweredByTag(telegramId, tagName)) {
                messageSender.sendPlainMessage(chatId, "❗\uFE0F Вы уже ответили на все вопросы " +
                        "с тегом #" + messageSender.escapeTagForMarkdown(tagName) + "!\n\n" +
                        "Если хотите пройти их заново, нужно сбросить счет." +
                        "Для этого воспользуйтесь командой /reset_score");
                return;
            }
            messageSender.sendMessage(chatId, "❌ Не найдено вопросов с тегом #" +
                    messageSender.escapeTagForMarkdown(tagName) + ".\n\n" +
                    "Убедитесь, что:\n" +
                    "• Тег существует\n" +
                    "• В базе есть вопросы с этим тегом\n" +
                    "• Используйте команду /list\\_tags для просмотра доступных тегов");
            return;
        }

        messageSender.sendPoll(chatId, quiz.question(), quiz.options(),
                quiz.correctOption(), "\uD83C\uDFF7\uFE0F [#"
                        + messageSender.escapeTagForMarkdown(tagName) + "] ");
    }
}
