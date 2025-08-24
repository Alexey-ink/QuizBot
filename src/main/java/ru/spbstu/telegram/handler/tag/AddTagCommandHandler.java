package ru.spbstu.telegram.handler.tag;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.spbstu.telegram.handler.CommandHandler;
import ru.spbstu.model.Tag;
import ru.spbstu.repository.TagRepository;
import ru.spbstu.service.UserService;
import ru.spbstu.telegram.sender.MessageSender;
import ru.spbstu.telegram.session.AddTagSession;
import ru.spbstu.telegram.utils.SessionManager;

@Component
public class AddTagCommandHandler extends CommandHandler {
    private final TagRepository tagRepository;
    private final UserService userService;
    private final SessionManager sessionManager;

    public AddTagCommandHandler(MessageSender messageSender,
                                TagRepository tagRepository,
                                UserService userService,
                                SessionManager sessionManager) {
        super(messageSender);
        this.tagRepository = tagRepository;
        this.userService = userService;
        this.sessionManager = sessionManager;
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
        String[] parts = text.split(" ");

        if(text.equals("/add_tag")) {
            sessionManager.getOrCreate(telegramId, AddTagSession.class);
            messageSender.sendMessage(update.getMessage().getChatId(),
                    "🏷 Введите название тега (англ./рус., без пробелов):");
            return;
        }

        String tagName = parts[0].trim();
        sessionManager.getOrCreate(telegramId, AddTagSession.class);

        if (parts.length > 1 || tagName.isEmpty()) {
            messageSender.sendMessage(update.getMessage().getChatId(),
                "❌ Название тега не должно содержать пробелы.");
            return;
        }

        try {
            var user = userService.getUser(telegramId);

            if (tagRepository.findByUserIdAndNameIgnoreCase(user.getId(), tagName).isPresent()) {
                messageSender.sendMessage(update.getMessage().getChatId(),
                    "❌ Тег #" + messageSender.escapeTagForMarkdown(tagName) + " уже существует.");
                return;
            }

            Tag newTag = new Tag();
            newTag.setUser(user);
            newTag.setName(tagName);

            tagRepository.save(newTag);

            messageSender.sendMessage(update.getMessage().getChatId(),
                "✅ Тег #" + messageSender.escapeTagForMarkdown(tagName) + " добавлен!");

        } catch (Exception e) {
            messageSender.sendMessage(update.getMessage().getChatId(),
                "❌ Ошибка при создании тега: " + e.getMessage());
        }
    }
}
