package ru.spbstu.telegram.handler.tag;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.spbstu.telegram.handler.CommandHandler;
import ru.spbstu.model.Tag;
import ru.spbstu.service.TagService;
import ru.spbstu.service.UserService;
import ru.spbstu.telegram.sender.MessageSender;

import java.util.List;

@Component
public class ListTagsCommandHandler extends CommandHandler {
    private final TagService tagService;
    private final UserService userService;

    public ListTagsCommandHandler(MessageSender messageSender,
                                  TagService tagService,
                                  UserService userService) {
        super(messageSender);
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
    public void handle(Update update) {
        Long telegramId = update.getMessage().getFrom().getId();
        
        try {
            List<Tag> tags = tagService.findAll();
            
            if (tags.isEmpty()) {
                messageSender.sendMessage(update.getMessage().getChatId(),
                    "ℹ️ У вас пока нет тегов.\n\n💡 **Создайте первый тег:** `/add_tag <название>`\n" +
                    "📝 **Затем добавьте вопрос:** `/add_question`");
                return;
            }
            
            StringBuilder response = new StringBuilder();
            response.append("🏷️ **Ваши теги:**\n\n");
            
            for (Tag tag : tags) {
                response.append("• #")
                        .append(messageSender.escapeTagForMarkdown(tag.getName()))
                        .append("\n");
            }
            
            response.append("\n💡 **Использование:**\n" +
                "• `/show_questions_by_tag <тег>` - просмотр вопросов\n" +
                "• `/delete_tag <тег>` - удаление тега\n(⚠️ удаляет вопросы без других тегов)");

            messageSender.sendMessage(update.getMessage().getChatId(), response.toString());
            
        } catch (Exception e) {
            messageSender.sendMessage(update.getMessage().getChatId(),
                "❌ Ошибка при получении тегов: " + e.getMessage());
        }
    }
}
