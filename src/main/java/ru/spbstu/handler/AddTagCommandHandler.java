package ru.spbstu.handler;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.spbstu.model.Tag;
import ru.spbstu.repository.TagRepository;
import ru.spbstu.service.UserService;

@Component
public class AddTagCommandHandler implements CommandHandler {
    private final TagRepository tagRepository;
    private final UserService userService;

    public AddTagCommandHandler(TagRepository tagRepository, UserService userService) {
        this.tagRepository = tagRepository;
        this.userService = userService;
    }

    @Override
    public String getCommand() {
        return "/add_tag";
    }

    @Override
    public void handle(Update update, AbsSender sender) {
        String text = update.getMessage().getText();
        String[] parts = text.split(" ", 2);
        
        if (parts.length < 2) {
            // Если команда без параметров - запрашиваем название тега
            sendMessage(sender, update.getMessage().getChatId(), 
                "🏷 Введите название тега (англ./рус., без пробелов):");
            return;
        }

        String tagName = parts[1].trim();
        Long telegramId = update.getMessage().getFrom().getId();
        
        // Проверяем корректность названия тега
        if (tagName.contains(" ") || tagName.isEmpty()) {
            sendMessage(sender, update.getMessage().getChatId(),
                "❌ Название тега не должно содержать пробелы.");
            return;
        }
        
        try {
            var user = userService.getUser(telegramId);
            
            // Проверяем, существует ли уже такой тег у пользователя
            if (tagRepository.findByUserIdAndNameIgnoreCase(user.getId(), tagName).isPresent()) {
                sendMessage(sender, update.getMessage().getChatId(),
                    "❌ Тег «" + tagName + "» уже существует.");
                return;
            }
            
            // Создаем новый тег
            Tag newTag = new Tag();
            newTag.setUser(user);
            newTag.setName(tagName);
            
            tagRepository.save(newTag);
            
            sendMessage(sender, update.getMessage().getChatId(),
                "✅ Тег «" + tagName + "» добавлен!");
                
        } catch (Exception e) {
            sendMessage(sender, update.getMessage().getChatId(),
                "❌ Ошибка при создании тега: " + e.getMessage());
        }
    }

    private void sendMessage(AbsSender sender, Long chatId, String text) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));
            message.setText(text);
            message.enableMarkdown(true);
            sender.execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
