package ru.spbstu.telegram.handler.general;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.spbstu.telegram.handler.CommandHandler;
import ru.spbstu.telegram.sender.MessageSender;

@Component
public class HealthcheckHandler extends CommandHandler {

    protected HealthcheckHandler(MessageSender messageSender) {
        super(messageSender);
    }

    @Override
    public String getCommand() { return "/healthcheck"; }

    @Override
    public String getDescription() {
        return "Проверить статус приложения и получить информацию об авторах";
    }

    @Override
    public void handle(Update update) {
        Long telegramId = update.getMessage().getFrom().getId();

        logger.info("Обработка команды /healthcheck от пользователя {}", telegramId);

        String text = "🟢 *Статус приложения: АКТИВНО*\n\n" +
                "👥 *Авторы-студенты:*\n" +
                "— Шихалев Алексей\n" +
                "— Емешкин Максим\n\n" +
                "Проект разработан в рамках учебной дисциплины \n" +
                "\"Программирование на языке JAVA\"";
        messageSender.sendMessage(telegramId, text);
    }
}
