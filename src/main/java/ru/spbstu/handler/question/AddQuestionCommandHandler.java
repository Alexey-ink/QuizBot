package ru.spbstu.handler.question;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.spbstu.handler.CommandHandler;
import ru.spbstu.service.QuestionService;
import ru.spbstu.session.AddQuestionSession;
import ru.spbstu.utils.SessionManager;

import java.util.Arrays;

@Component
public class AddQuestionCommandHandler implements CommandHandler {
    private final SessionManager sessionManager;
    private final QuestionService questionService;

    public AddQuestionCommandHandler(SessionManager sessionManager, QuestionService questionService) {
        this.sessionManager = sessionManager;
        this.questionService = questionService;
    }

    @Override
    public String getCommand() {
        return "/add_question";
    }

    @Override
    public String getDescription() {
        return "Добавить новый вопрос с вариантами ответов";
    }

    @Override
    public void handle(Update update, AbsSender sender) {
        var userId = update.getMessage().getFrom().getId();
        var chatId = update.getMessage().getChatId();
        var text = update.getMessage().getText();

        AddQuestionSession session = sessionManager.getOrCreate(userId, AddQuestionSession.class);

        if (text.equals("/add_question")) {
            session.setStep(AddQuestionSession.Step.ASK_QUESTION_TEXT);
            sendMessage(sender, chatId, "📝 Введите текст вопроса (макс. 200 символов):");
            return;
        }

        switch (session.getStep()) {
            case ASK_QUESTION_TEXT -> {
                if (text.trim().isEmpty() || text.length() > 200) {
                    sendMessage(sender, chatId, "❌ Текст вопроса должен содержать от 1 до 200 символов.");
                    return;
                }
                session.setQuestionText(text.trim());
                session.setStep(AddQuestionSession.Step.ASK_ANSWER_OPTIONS);
                sendMessage(sender, chatId, "🔢 Введите вариант 1:");
            }
            case ASK_ANSWER_OPTIONS -> {
                session.getOptions().add(text.trim());
                if (session.getOptions().size() < 4) {
                    sendMessage(sender, chatId, "🔢 Введите вариант " + (session.getOptions().size() + 1) + ":");
                } else {
                    session.setStep(AddQuestionSession.Step.ASK_CORRECT_OPTION);
                    sendMessage(sender, chatId, "Введите номер правильного варианта (1-4):");
                }
            }
            case ASK_CORRECT_OPTION -> {
                try {
                    int num = Integer.parseInt(text.trim());
                    if (num < 1 || num > 4) {
                        sendMessage(sender, chatId, "❌ Номер должен быть от 1 до 4.");
                        return;
                    }
                    session.setCorrectOption(num);
                    session.setStep(AddQuestionSession.Step.ASK_TAGS);
                    sendMessage(sender, chatId, "Введите теги (через запятую):");
                } catch (NumberFormatException e) {
                    sendMessage(sender, chatId, "❌ Введите число от 1 до 4.");
                }
            }
            case ASK_TAGS -> {
                session.setTags(Arrays.stream(text.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList());
                session.setStep(AddQuestionSession.Step.FINISHED);
                Long telegramId = update.getMessage().getFrom().getId();
                String questionId = questionService.saveQuestion(telegramId,
                        session.getQuestionText(),
                        session.getOptions(),
                        session.getCorrectOption(),
                        session.getTags());
                SendMessage message = new SendMessage();
                message.setChatId(chatId.toString());
                message.setText(
                        "✅ Вопрос сохранен!\n" +
                        "🆔 ID: <code>" + questionId + "</code>\n\n"
                );
                message.setParseMode("HTML");
                try {
                    sender.execute(message);
                } catch (TelegramApiException e) {
                    throw new RuntimeException(e);
                }
                sessionManager.clearSession(userId);
            }
            default -> sendMessage(sender, chatId, "Процесс уже завершен. Начните заново: /add_question");
        }
    }

}
