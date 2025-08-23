package ru.spbstu.handler.tag;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.bots.AbsSender;
import ru.spbstu.handler.CommandHandler;
import ru.spbstu.repository.TagRepository;
import ru.spbstu.model.Tag;
import ru.spbstu.service.TagService;
import ru.spbstu.service.UserService;

import java.util.List;

@Component
public class ListTagsCommandHandler implements CommandHandler {
    private final TagService tagService;
    private final UserService userService;

    public ListTagsCommandHandler(TagService tagService, UserService userService) {
        this.tagService = tagService;
        this.userService = userService;
    }

    @Override
    public String getCommand() {
        return "/list_tags";
    }

    @Override
    public String getDescription() {
        return "Показать список всех ваших тегов";
    }

    @Override
    public void handle(Update update, AbsSender sender) {
        Long telegramId = update.getMessage().getFrom().getId();
        
        try {
            var user = userService.getUser(telegramId);
            List<Tag> tags = tagService.findAllByUserId(user.getId());
            
            if (tags.isEmpty()) {
                sendMessage(sender, update.getMessage().getChatId(), 
                    "ℹ️ У вас пока нет тегов.\n\n💡 **Создайте первый тег:** `/add_tag <название>`\n" +
                    "📝 **Затем добавьте вопрос:** `/add_question`");
                return;
            }
            
            StringBuilder response = new StringBuilder();
            response.append("🏷️ **Ваши теги:**\n\n");
            
            for (Tag tag : tags) {
                response.append("• #")
                        .append(tagService.escapeTagForMarkdown(tag.getName()))
                        .append("\n");
            }
            
            response.append("\n💡 **Использование:**\n" +
                "• `/show_questions_by_tag <тег>` - просмотр вопросов\n" +
                "• `/delete_tag <тег>` - удаление тега\n(⚠️ удаляет вопросы без других тегов)");
            
            sendMessage(sender, update.getMessage().getChatId(), response.toString());
            
        } catch (Exception e) {
            sendMessage(sender, update.getMessage().getChatId(), 
                "❌ Ошибка при получении тегов: " + e.getMessage());
        }
    }
}
