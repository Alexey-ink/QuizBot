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
        Long telegramId = update.getMessage().getFrom().getId();

        logger.info("Обработка команды /help от пользователя {}", telegramId);

        String helpText = """
            🤖 Команды Quiz Bot
            
            📝 *Управление вопросами:*
            • `/add_question` - Добавить новый вопрос
            • `/show_questions_by_tag <тег>` - Показать вопросы по тегу
            • `/delete_question <ID>` - Удалить вопрос по ID
            
            🎲 *Викторины:*
            • `/random` - Случайный вопрос
            • `/random_by_tag <тег>` - Случайный вопрос по тегу
            
            🏷️ *Управление тегами:*
            • `/add_tag` - Добавить новый тег
            • `/list_tags` - Список всех тегов
            • `/delete_tag <тег>` - Удалить тег
            
            📊 *Статистика:*
            • `/score` - Показать общее количество баллов
            • `/score_by_tag <тег>` - Показать количество балло по тегу
            • `/reset_score` - Сбросить свой счёт (Все вопросы откроются заново для вас)
            
            ⏰ *Расписание:*
            • `/schedule` - Настройка автоматической отправки вопросов по расписанию
            • `/unschedule` - Отключить автоматическую отправку вопросов
            
            💡 *Обратная связь:*
            Если у вас есть предложения или вы нашли ошибку — напишите @AlexeyShihalev.
            """;

        messageSender.sendMessage(telegramId, helpText);
    }
}
