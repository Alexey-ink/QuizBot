package ru.spbstu.telegram.handler.tag;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.spbstu.dto.TagDto;
import ru.spbstu.service.TagService;
import ru.spbstu.telegram.handler.CommandHandler;
import ru.spbstu.service.QuestionService;
import ru.spbstu.telegram.sender.MessageSender;
import ru.spbstu.telegram.utils.SessionManager;
import ru.spbstu.telegram.session.DeleteTagConfirmationSession;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Component
public class DeleteTagCommandHandler extends CommandHandler {
    private final TagService tagService;
    private final QuestionService questionService;
    private final SessionManager sessionManager;

    private final Map<Long, TagDto> pendingTagDeletions = new ConcurrentHashMap<>();

    public DeleteTagCommandHandler(MessageSender messageSender, TagService tagService,
                                   QuestionService questionService,
                                   SessionManager sessionManager) {
        super(messageSender);
        this.tagService = tagService;
        this.questionService = questionService;
        this.sessionManager = sessionManager;
    }

    @Override
    public String getCommand() {
        return "/delete_tag";
    }

    @Override
    public String getDescription() {
        return "Удалить тег и связанные с ним вопросы";
    }

    @Override
    public void handle(Update update) {
        String text = update.getMessage().getText();
        Long telegramId = update.getMessage().getFrom().getId();

        if (text.equals("/delete_tag")) {
            messageSender.sendMessage(telegramId,
                    "❌ Укажите тег.\nИспользование: `/delete_tag <тег>`");
            return;
        }

        String[] parts = text.split(" ");
        if (parts.length > 2) {
            messageSender.sendMessage(update.getMessage().getChatId(),
                    "❌ Укажите один тег без пробелов.\nИспользование: `/delete_tag <тег>`");
            return;
        }

        String tagName = parts[1].trim();
        Optional<TagDto> tagDtoOptional = tagService.findByNameIgnoreCase(tagName);
        if (tagDtoOptional.isEmpty()) {
            messageSender.sendMessage(update.getMessage().getChatId(),
                    "❌ Тег #" + messageSender.escapeTagForMarkdown(tagName) + " не существует.");
            return;
        }

        TagDto tagDto = tagDtoOptional.get();
        if (!telegramId.equals(tagDto.telegramId())) {
            messageSender.sendMessage(update.getMessage().getChatId(),
                    "❌ Тег #" + messageSender.escapeTagForMarkdown(tagName) +
                            " создан другим пользователем. Вы не можете его удалить");
            return;
        }

        sessionManager.getOrCreate(telegramId, DeleteTagConfirmationSession.class);
        String confirmationMessage = "❗ Удаление тега #" +
                messageSender.escapeTagForMarkdown(tagName) +
                " также удалит ВАШИ вопросы, у которых нет других тегов. Продолжить? (Да/Нет)";
        messageSender.sendMessage(update.getMessage().getChatId(), confirmationMessage);

        pendingTagDeletions.put(telegramId, tagDto);
    }

    public void handleDeleteTagConfirmation(Update update) {
        Long telegramId = update.getMessage().getFrom().getId();
        String text = update.getMessage().getText().toLowerCase().trim();

        if (text.equals("да") || text.equals("yes")) {
            this.deleteTag(telegramId);
            messageSender.sendMessage(update.getMessage().getChatId(), "✅ Тег удален.");
        } else if (text.equals("нет") || text.equals("no") || text.equals("n")) {
            pendingTagDeletions.remove(telegramId);
            sessionManager.clearSession(telegramId);
            messageSender.sendMessage(update.getMessage().getChatId(), "❗ Отменено.");
        } else {
            messageSender.sendMessage(update.getMessage().getChatId(),
                    "Пожалуйста, ответьте «Да» или «Нет» для подтверждения удаления тега.");
        }
    }

    public void deleteTag(Long telegramId) {
        TagDto tagDto = pendingTagDeletions.get(telegramId);

        questionService.deleteQuestionsWithSingleTag(
                tagDto.userId(),
                tagDto.id()
        );

        if(questionService.existsQuestionsByTagId(tagDto.id())) {
            questionService.deleteTagFromQuestionsByTagId(tagDto.id(), tagDto.userId());
        }
        else {
            tagService.deleteScoreByTagId(tagDto.id());
            tagService.deleteTagById(tagDto.id());
        }

        pendingTagDeletions.remove(telegramId);
        sessionManager.clearSession(telegramId);
    }
}

