package ru.spbstu.telegram.handler.tag;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.spbstu.telegram.handler.CommandHandler;
import ru.spbstu.telegram.sender.MessageSender;
import ru.spbstu.telegram.utils.SessionManager;
import ru.spbstu.telegram.session.DeleteTagConfirmationSession;
import ru.spbstu.service.QuestionService;
import ru.spbstu.service.TagService;
import ru.spbstu.dto.TagDto;

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

        logger.info("Обработка команды /delete_tag от пользователя {}: {}", telegramId, text);

        try {
            if (sessionManager.hasSession(telegramId)) {
                logger.debug("Обработка подтверждения удаления тега пользователем {}", telegramId);
                handleDeleteTagConfirmation(update);
                return;
            }

            if (text.equals("/delete_tag")) {
                logger.warn("Не указан тег для удаления пользователем {}", telegramId);
                messageSender.sendMessage(telegramId,
                        "❌ Укажите тег.\nИспользование: `/delete_tag <тег>`\n\n" +
                                "Команда удалит тег только в том случае, " +
                                "если по этому тегу нет вопросов, созданных другими пользователями");
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
                logger.warn("Тег '{}' не существует (пользователь {})", tagName, telegramId);
                messageSender.sendMessage(update.getMessage().getChatId(),
                        "❌ Тег #" + messageSender.escapeTagForMarkdown(tagName) + " не существует.");
                return;
            }

            TagDto tagDto = tagDtoOptional.get();
            if (!telegramId.equals(tagDto.telegramId())) {
                logger.warn("Попытка удалить чужой тег '{}' пользователем {}", tagName, telegramId);
                messageSender.sendMessage(update.getMessage().getChatId(),
                        "❌ Тег #" + messageSender.escapeTagForMarkdown(tagName) +
                                " создан другим пользователем. Вы не можете его удалить.");
                return;
            }

            sessionManager.getOrCreate(telegramId, DeleteTagConfirmationSession.class);
            String confirmationMessage = "❗ Удаление тега #" +
                    messageSender.escapeTagForMarkdown(tagName) +
                    " также удалит ВАШИ вопросы, у которых нет других тегов. Если у этого тега есть вопросы, " +
                    "созданные другими пользователями, тег не удалится. Продолжить? (Да/Нет)";
            messageSender.sendMessage(update.getMessage().getChatId(), confirmationMessage);

            pendingTagDeletions.put(telegramId, tagDto);
            logger.info("Запрос подтверждения удаления тега '{}' от пользователя {}", tagName, telegramId);

        } catch (Exception e) {
            logger.error("Ошибка при обработке удаления тега пользователем {}: {}", telegramId, e.getMessage(), e);
            messageSender.sendMessage(telegramId, "❌ Произошла ошибка при обработке команды");
        }
    }

    public void handleDeleteTagConfirmation(Update update) {
        Long telegramId = update.getMessage().getFrom().getId();
        String text = update.getMessage().getText().toLowerCase().trim();
        logger.debug("Обработка подтверждения удаления тега пользователем {}: {}",
                telegramId, text);

        try {
            if (text.equals("да") || text.equals("yes")) {
                this.deleteTag(telegramId);
                messageSender.sendMessage(update.getMessage().getChatId(), "✅ Тег удален.");
            } else if (text.equals("нет") || text.equals("no") || text.equals("n")) {
                pendingTagDeletions.remove(telegramId);
                sessionManager.clearSession(telegramId);
                messageSender.sendMessage(update.getMessage().getChatId(), "❗ Отменено.");
                logger.info("Удаление тега отменено пользователем {}", telegramId);
            } else {
                messageSender.sendMessage(update.getMessage().getChatId(),
                        "Пожалуйста, ответьте «Да» или «Нет» для подтверждения удаления тега.");
            }
        } catch (Exception e) {
            logger.error("Ошибка при обработке подтверждения пользователем {}: {}",
                    telegramId, e.getMessage(), e);
            messageSender.sendMessage(telegramId, "❌ Произошла ошибка при обработке подтверждения");
        }
    }

    public void deleteTag(Long telegramId) {
        try {
            TagDto tagDto = pendingTagDeletions.get(telegramId);

            questionService.deleteQuestionsWithSingleTag(
                    tagDto.userId(),
                    tagDto.id()
            );

            if (questionService.existsQuestionsByTagId(tagDto.id())) {
                logger.debug("Удаление тега '{}' из вопросов пользователем {}", tagDto.name(), telegramId);
                questionService.deleteTagFromQuestionsByTagId(tagDto.id(), tagDto.userId());
            } else {
                logger.debug("Полное удаление тега '{}' пользователем {}", tagDto.name(), telegramId);
                tagService.deleteScoreByTagId(tagDto.id());
                tagService.deleteTagById(tagDto.id());
            }

            pendingTagDeletions.remove(telegramId);
            sessionManager.clearSession(telegramId);

            logger.info("Тег '{}' полностью обработан при удалении пользователем {}",
                    tagDto.name(), telegramId);
        } catch (Exception e) {
            logger.error("Ошибка при удалении тега пользователем {}: {}", telegramId, e.getMessage(), e);
            throw new RuntimeException("Ошибка при удалении тега", e);
        }
    }
}

