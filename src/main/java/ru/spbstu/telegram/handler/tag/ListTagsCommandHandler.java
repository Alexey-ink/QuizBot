package ru.spbstu.telegram.handler.tag;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.spbstu.dto.TagDto;
import ru.spbstu.telegram.handler.CommandHandler;
import ru.spbstu.service.TagService;
import ru.spbstu.telegram.sender.MessageSender;

import java.util.List;

@Component
public class ListTagsCommandHandler extends CommandHandler {
    private final TagService tagService;

    public ListTagsCommandHandler(MessageSender messageSender,
                                  TagService tagService) {
        super(messageSender);
        this.tagService = tagService;
    }

    @Override
    public String getCommand() {
        return "/list_tags";
    }

    @Override
    public String getDescription() {
        return "Показать список всех тегов";
    }

    @Override
    public void handle(Update update) {
        List<TagDto> tagsDto = tagService.findAll();

        if (tagsDto.isEmpty()) {
            messageSender.sendMessage(update.getMessage().getChatId(),
                "ℹ️ В базе пока нет тегов.\n\n💡 **Создайте первый тег:** `/add_tag <название>`\n" +
                "📝 **Затем добавьте вопрос:** `/add_question`");
            return;
        }

        StringBuilder response = new StringBuilder();
        response.append("🏷️ **Ваши теги:**\n\n");

        for (TagDto tag : tagsDto) {
            response.append("• #")
                    .append(messageSender.escapeTagForMarkdown(tag.name()))
                    .append("\n");
        }

        response.append("\n💡 **Использование:**\n" +
            "• `/show_questions_by_tag <тег>` - просмотр вопросов\n" +
            "• `/delete_tag <тег>` - удаление тега\n(⚠️ удаляет вопросы без других тегов)");

        messageSender.sendMessage(update.getMessage().getChatId(), response.toString());

    }
}
