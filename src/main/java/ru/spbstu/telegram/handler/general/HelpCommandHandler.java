package ru.spbstu.telegram.handler.general;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.spbstu.telegram.handler.CommandHandler;
import ru.spbstu.telegram.sender.MessageSender;

@Component
public class HelpCommandHandler extends CommandHandler {

    protected HelpCommandHandler(MessageSender messageSender) {
        super(messageSender);
    }

    @Override
    public String getCommand() {
        return "/help";
    }

    @Override
    public String getDescription() {
        return "Показать справку по всем командам";
    }

    @Override
    public void handle(Update update) {
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
            
            📊 **Статистика:**
            • `/score` - Показать общее количество баллов
            • `/score_by_tag <тег>` - Показать количество балло по тегу
            
            ⏰ **Расписание:**
            • `/schedule` - Настройка автоматической отправки вопросов по расписанию
            • `/unschedule` - Отключить автоматическую отправку вопросов
            """;

        messageSender.sendMessage(update.getMessage().getChatId(), helpText);
    }
}
