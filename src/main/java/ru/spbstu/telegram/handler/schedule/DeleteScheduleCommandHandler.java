package ru.spbstu.telegram.handler.schedule;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.spbstu.telegram.handler.CommandHandler;
import ru.spbstu.telegram.sender.MessageSender;
import ru.spbstu.telegram.session.schedule.DeleteScheduleSession;
import ru.spbstu.telegram.utils.SessionManager;
import ru.spbstu.service.ScheduleService;
import ru.spbstu.model.Schedule;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DeleteScheduleCommandHandler extends CommandHandler {

    private final ScheduleService scheduleService;
    private final SessionManager sessionManager;
    private final Map<Long, List<Schedule>> pendingDeletes = new ConcurrentHashMap<>();

    public DeleteScheduleCommandHandler(MessageSender messageSender,
                                        ScheduleService scheduleService,
                                        SessionManager sessionManager) {
        super(messageSender);
        this.scheduleService = scheduleService;
        this.sessionManager = sessionManager;
    }

    @Override
    public String getCommand() {
        return "/unschedule";
    }

    @Override
    public String getDescription() {
        return "Отключить автоматическую отправку вопросов";
    }

    @Override
    public void handle(Update update) {
        String message = update.getMessage().getText();
        Long chatId = update.getMessage().getChatId();
        Long telegramId = update.getMessage().getFrom().getId();

        logger.info("Обработка команды /unschedule от пользователя {}: {}", telegramId, message);
        try {
            DeleteScheduleSession session = sessionManager.getOrCreate(telegramId, DeleteScheduleSession.class);

            if (pendingDeletes.containsKey(telegramId)) {
                logger.debug("Обработка выбора расписания для удаления пользователем {}", telegramId);
                List<Schedule> list = pendingDeletes.get(telegramId);
                if ("/cancel".equalsIgnoreCase(message)) {
                    pendingDeletes.remove(telegramId);
                    sessionManager.clearSession(telegramId);
                    messageSender.sendMessage(chatId, "Операция отменена.");
                    logger.info("Удаление расписания отменено пользователем {}", telegramId);
                    return;
                }

                int choice;
                try {
                    choice = Integer.parseInt(message);
                } catch (NumberFormatException e) {
                    logger.debug("Неверный формат номера от пользователя {}: {}", telegramId, message);
                    messageSender.sendMessage(chatId, "Пожалуйста, отправьте номер расписания (число) или /cancel для отмены.");
                    return;
                }

                if (choice < 1 || choice > list.size()) {
                    messageSender.sendMessage(chatId, "Неверный номер. Выберите число от 1 до " + list.size() + " или /cancel.");
                    return;
                }

                Schedule chosen = list.get(choice - 1);
                try {
                    scheduleService.deleteSchedule(chosen.getId());
                    sessionManager.clearSession(telegramId);
                    messageSender.sendMessage(chatId, "Расписание с id=" + chosen.getId() + " удалено.");
                } catch (Exception ex) {
                    ex.printStackTrace();
                    messageSender.sendMessage(chatId, "Не удалось удалить расписание: " + ex.getMessage());
                } finally {
                    pendingDeletes.remove(telegramId);
                }
                return;
            }

            List<Schedule> schedules = scheduleService.findAllSchedulesByUserTelegramId(telegramId);
            if (schedules == null || schedules.isEmpty()) {
                logger.info("У пользователя {} нет расписаний для удаления", telegramId);
                messageSender.sendMessage(chatId, "У вас нет сохранённых расписаний.");
                return;
            }

            logger.debug("Найдено {} расписаний для пользователя {}", schedules.size(), telegramId);

            StringBuilder sb = new StringBuilder();
            sb.append("Ваши расписания:\n\n");
            for (int i = 0; i < schedules.size(); i++) {
                Schedule s = schedules.get(i);
                sb.append(i + 1).append(") ");
                sb.append("id=").append(s.getId());
                try {
                    if (s.getCronExpression() != null) {
                        sb.append(", cron=").append(s.getCronExpression());
                    }
                } catch (Exception ignored) {
                }
                sb.append("\n");
            }
            sb.append("\nОтправьте номер расписания (например: 1) чтобы удалить, или /cancel чтобы отменить.");

            pendingDeletes.put(telegramId, schedules);
            messageSender.sendMessage(chatId, sb.toString());
            logger.info("Список расписаний отправлен пользователю {} для выбора удаления", telegramId);
        } catch (Exception e) {
            logger.error("Ошибка при обработке команды /unschedule пользователем {}: {}",
                    telegramId, e.getMessage(), e);
            messageSender.sendMessage(chatId, "❌ Произошла ошибка при обработке команды");
        }
    }
}