package ru.spbstu.telegram.handler.question;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.spbstu.telegram.handler.CommandHandler;
import ru.spbstu.service.QuestionService;
import ru.spbstu.service.TagService;
import ru.spbstu.telegram.sender.MessageSender;
import ru.spbstu.telegram.session.AddQuestionSession;
import ru.spbstu.telegram.utils.SessionManager;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class AddQuestionCommandHandler extends CommandHandler {
    private final SessionManager sessionManager;
    private final QuestionService questionService;
    private final TagService tagService;

    public AddQuestionCommandHandler(MessageSender messageSender,
                                     SessionManager sessionManager,
                                     QuestionService questionService,
                                     TagService tagService) {
        super(messageSender);
        this.sessionManager = sessionManager;
        this.questionService = questionService;
        this.tagService = tagService;
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
    public void handle(Update update) {
        var userId = update.getMessage().getFrom().getId();
        var chatId = update.getMessage().getChatId();
        var text = update.getMessage().getText();

        AddQuestionSession session = sessionManager.getOrCreate(userId, AddQuestionSession.class);

        if (text.equals("/add_question")) {
            session.setStep(AddQuestionSession.Step.ASK_QUESTION_TEXT);
            messageSender.sendMessage(chatId, "📝 Введите текст вопроса (макс. 200 символов):");
            return;
        }

        switch (session.getStep()) {
            case ASK_QUESTION_TEXT -> {
                if (text.trim().isEmpty() || text.length() > 200) {
                    messageSender.sendMessage(chatId, "❌ Текст вопроса должен содержать от 1 до 200 символов.");
                    return;
                }
                session.setQuestionText(text.trim());
                session.setStep(AddQuestionSession.Step.ASK_ANSWER_OPTIONS);
                messageSender.sendMessage(chatId, "🔢 Введите вариант 1:");
            }
            case ASK_ANSWER_OPTIONS -> {
                if (text.trim().isEmpty() || text.length() > 200) {
                    messageSender.sendMessage(chatId, "❌ Текст варианта ответа должен содержать от 1 до 200 символов.");
                    return;
                }
                session.getOptions().add(text.trim());
                if (session.getOptions().size() < 4) {
                    messageSender.sendMessage(chatId, "🔢 Введите вариант " + (session.getOptions().size() + 1) + ":");
                } else {
                    session.setStep(AddQuestionSession.Step.ASK_CORRECT_OPTION);
                    messageSender.sendMessage(chatId, "Введите номер правильного варианта (1-4):");
                }
            }
            case ASK_CORRECT_OPTION -> {
                try {
                    int num = Integer.parseInt(text.trim());
                    if (num < 1 || num > 4) {
                        messageSender.sendMessage(chatId, "❌ Номер должен быть от 1 до 4.");
                        return;
                    }
                    session.setCorrectOption(num);
                    session.setStep(AddQuestionSession.Step.ASK_TAGS);
                    messageSender.sendMessage(chatId, "Введите теги (через запятую):");
                } catch (NumberFormatException e) {
                    messageSender.sendMessage(chatId, "❌ Введите число от 1 до 4.");
                }
            }
            case ASK_TAGS -> {
                try {
                    List<String> tags = tagService.parseAndValidateTags(text);

                    session.setTags(tags);
                    session.setStep(AddQuestionSession.Step.FINISHED);

                    Long telegramId = update.getMessage().getFrom().getId();
                    String questionId = questionService.saveQuestion(telegramId,
                            session.getQuestionText(),
                            session.getOptions(),
                            session.getCorrectOption(),
                            session.getTags());

                    String message = "✅ Вопрос сохранен!\n" +
                            "\uD83C\uDFF7\uFE0F Теги: " + session.getTags().stream()
                            .map(tag -> "#" + messageSender.escapeTagForMarkdown(tag))
                            .collect(Collectors.joining(", ")) +
                            "\n🆔: `" + questionId +"`\n\n";

                    messageSender.sendMessage(userId, message);
                    sessionManager.clearSession(userId);
                } catch (IllegalArgumentException e) {
                    messageSender.sendPlainMessage(chatId, "❌ " + e.getMessage() + "\nВведите теги (через запятую):");
                }
            }
            default -> messageSender.sendMessage(chatId, "Процесс уже завершен. Начните заново: /add_question");
        }
    }

}
