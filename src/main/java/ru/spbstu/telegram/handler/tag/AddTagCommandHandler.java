package ru.spbstu.telegram.handler.tag;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.spbstu.telegram.handler.CommandHandler;
import ru.spbstu.model.Tag;
import ru.spbstu.repository.TagRepository;
import ru.spbstu.service.UserService;
import ru.spbstu.telegram.sender.MessageSender;

@Component
public class AddTagCommandHandler extends CommandHandler {
    private final TagRepository tagRepository;
    private final UserService userService;

    public AddTagCommandHandler(MessageSender messageSender,
                                TagRepository tagRepository,
                                UserService userService) {
        super(messageSender);
        this.tagRepository = tagRepository;
        this.userService = userService;
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
        String[] parts = text.split(" ", 2);
        
        if (parts.length < 2) {
            // Если команда без параметров - запрашиваем название тега
            messageSender.sendMessage(update.getMessage().getChatId(),
                "🏷 Введите название тега (англ./рус., без пробелов):");
            return;
        }

        String tagName = parts[1].trim();
        Long telegramId = update.getMessage().getFrom().getId();
        
        // Проверяем корректность названия тега
        if (tagName.contains(" ") || tagName.isEmpty()) {
            messageSender.sendMessage(update.getMessage().getChatId(),
                "❌ Название тега не должно содержать пробелы.");
            return;
        }
        
        try {
            var user = userService.getUser(telegramId);
            
            // Проверяем, существует ли уже такой тег у пользователя
            if (tagRepository.findByUserIdAndNameIgnoreCase(user.getId(), tagName).isPresent()) {
                messageSender.sendMessage(update.getMessage().getChatId(),
                    "❌ Тег «" + tagName + "» уже существует.");
                return;
            }

            Tag newTag = new Tag();
            newTag.setUser(user);
            newTag.setName(tagName);
            
            tagRepository.save(newTag);

            messageSender.sendMessage(update.getMessage().getChatId(),
                "✅ Тег «" + tagName + "» добавлен!");
                
        } catch (Exception e) {
            messageSender.sendMessage(update.getMessage().getChatId(),
                "❌ Ошибка при создании тега: " + e.getMessage());
        }
    }
}
