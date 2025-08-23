package ru.spbstu.handler.general;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.bots.AbsSender;
import ru.spbstu.handler.CommandHandler;

@Component
public class HelpCommandHandler implements CommandHandler {

    @Override
    public String getCommand() {
        return "/help";
    }

    @Override
    public String getDescription() {
        return "Показать справку по всем командам";
    }

    @Override
    public void handle(Update update, AbsSender sender) {
        String helpText = """
            🤖 **Команды Quiz Bot**
            
            📝 **Управление вопросами:**
            • `/add_question` - Добавить новый вопрос
            • `/show_questions_by_tag <тег>` - Показать вопросы по тегу
            • `/delete_question <ID>` - Удалить вопрос по ID
            
            🎲 **Викторины:**
            • `/random` - Случайный вопрос
            • `/random_by_tag <тег>` - Случайный вопрос по тегу
            
            🏷️ **Управление тегами:**
            • `/add_tag <название>` - Добавить новый тег
            • `/list_tags` - Список всех тегов
            • `/delete_tag <тег>` - Удалить тег
            
            • `/score` - Показать общее количество баллов
            • `/score_by_tag` - Показать количество балло по тегу (НЕ ГОТОВА ЕЩЕ)
            
            • `/schedule` - Настройка автоматической отправки вопросов по расписанию
            • `/unschedule` - Отключить автоматическую отправку вопросов
            """;

        sendMessage(sender, update.getMessage().getChatId(), helpText);
    }
}
