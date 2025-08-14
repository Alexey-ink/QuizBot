package ru.spbstu.handler;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Component
public class HelpCommandHandler implements CommandHandler {

    @Override
    public String getCommand() {
        return "/help";
    }

    @Override
    public void handle(Update update, AbsSender sender) {
        String helpText = """
            🤖 **Команды Quiz Bot**
            
            📝 **Управление вопросами:**
            • `/add_question` - Добавить новый вопрос
            • `/show_questions_by_tag <тег>` - Показать вопросы по тегу
            • `/delete_question <ID>` - Удалить вопрос по ID
            
            🏷️ **Управление тегами:**
            • `/add_tag <название>` - Добавить новый тег
            • `/list_tags` - Список всех тегов
            • `/delete_tag <тег>` - Удалить тег
            
            ❓ **Другое:**
            • `/start` - Запустить бота
            • `/help` - Показать эту справку
            
            💡 **Как использовать:**
            1. Сначала добавьте теги с помощью `/add_tag <название>`
            2. Добавьте вопросы с помощью `/add_question`
            3. Просмотрите ваши вопросы с помощью `/show_questions_by_tag <тег>`
            4. Удалите вопросы с помощью `/delete_question <ID>`
            5. Удалите теги с помощью `/delete_tag <тег>` (⚠️ убирает тег из вопросов, удаляет вопросы без других тегов)
            """;

        sendMessage(sender, update.getMessage().getChatId(), helpText);
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
