package ru.spbstu.telegram.handler.tag;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.spbstu.telegram.handler.CommandHandler;
import ru.spbstu.telegram.sender.MessageSender;
import ru.spbstu.telegram.session.AddTagSession;
import ru.spbstu.telegram.utils.SessionManager;
import ru.spbstu.service.TagService;

@Component
public class AddTagCommandHandler extends CommandHandler {
    private final SessionManager sessionManager;
    private final TagService tagService;

    public AddTagCommandHandler(MessageSender messageSender,
                                SessionManager sessionManager, TagService tagService) {
        super(messageSender);
        this.sessionManager = sessionManager;
        this.tagService = tagService;
    }

    @Override
    public String getCommand() {
        return "/add_tag";
    }

    @Override
    public String getDescription() {
        return "Добавить новый тег для категоризации вопросов";
    }

    @Override
    public void handle(Update update) {
        String text = update.getMessage().getText();
        Long telegramId = update.getMessage().getFrom().getId();

        logger.info("Обработка команды /add_tag от пользователя {}: {}", telegramId, text);
        try {
            String[] parts = text.split(" ");

            if (text.equals("/add_tag")) {
                logger.debug("Начало процесса добавления тега пользователем {}", telegramId);
                sessionManager.getOrCreate(telegramId, AddTagSession.class);
                messageSender.sendMessage(update.getMessage().getChatId(),
                        "🏷 Введите название тега (англ./рус., без пробелов):");
                return;
            }

            String tagName = parts[0].trim().toLowerCase();
            sessionManager.getOrCreate(telegramId, AddTagSession.class);

            if (parts.length > 1 || tagName.isEmpty()) {
                logger.warn("Неверный формат тега от пользователя {}: {}", telegramId, text);
                messageSender.sendMessage(update.getMessage().getChatId(),
                        "❌ Название тега не должно содержать пробелы.");
                return;
            }

            if (tagService.findByNameIgnoreCase(tagName).isPresent()) {
                logger.warn("Попытка добавить существующий тег '{}' пользователем {}", tagName, telegramId);
                messageSender.sendMessage(update.getMessage().getChatId(),
                        "❌ Тег #" + messageSender.escapeTagForMarkdown(tagName) + " уже существует.");
                return;
            }

            tagService.createNewTag(telegramId, tagName);
            sessionManager.clearSession(telegramId);

            messageSender.sendMessage(update.getMessage().getChatId(),
                    "✅ Тег #" + messageSender.escapeTagForMarkdown(tagName) + " добавлен!");

            logger.info("Тег '{}' успешно добавлен пользователем {}", tagName, telegramId);

        } catch (Exception e) {
            logger.error("Ошибка при добавлении тега пользователем {}: {}", telegramId, e.getMessage(), e);
            messageSender.sendMessage(telegramId, "❌ Произошла ошибка при добавлении тега");
        }
    }
}
