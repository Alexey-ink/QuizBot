package ru.spbstu.handler.schedule;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.bots.AbsSender;
import ru.spbstu.handler.CommandHandler;
import ru.spbstu.model.Schedule;
import ru.spbstu.service.ScheduleService;
import ru.spbstu.session.schedule.DeleteScheduleSession;
import ru.spbstu.utils.SessionManager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DeleteScheduleCommandHandler implements CommandHandler {

    private final ScheduleService scheduleService;
    private final SessionManager sessionManager;
    private final Map<Long, List<Schedule>> pendingDeletes = new ConcurrentHashMap<>();

    public DeleteScheduleCommandHandler(ScheduleService scheduleService, SessionManager sessionManager) {
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
    public void handle(Update update, AbsSender sender) {
        String message = update.getMessage().getText();
        Long chatId = update.getMessage().getChatId();
        Long userId = update.getMessage().getFrom().getId();

        DeleteScheduleSession session = sessionManager.getOrCreate(userId, DeleteScheduleSession.class);

        if (pendingDeletes.containsKey(userId)) {
            List<Schedule> list = pendingDeletes.get(userId);
            if ("/cancel".equalsIgnoreCase(message)) {
                pendingDeletes.remove(userId);
                sessionManager.clearSession(userId);
                sendMessage(sender, chatId, "Операция отменена.");
                return;
            }

            int choice;
            try {
                choice = Integer.parseInt(message);
            } catch (NumberFormatException e) {
                sendMessage(sender, chatId, "Пожалуйста, отправьте номер расписания (число) или /cancel для отмены.");
                return;
            }

            if (choice < 1 || choice > list.size()) {
                sendMessage(sender, chatId, "Неверный номер. Выберите число от 1 до " + list.size() + " или /cancel.");
                return;
            }

            Schedule chosen = list.get(choice - 1);
            try {
                scheduleService.deleteSchedule(chosen.getId());
                sessionManager.clearSession(userId);
                sendMessage(sender, chatId, "Расписание с id=" + chosen.getId() + " удалено.");
            } catch (Exception ex) {
                ex.printStackTrace();
                sendMessage(sender, chatId, "Не удалось удалить расписание: " + ex.getMessage());
            } finally {
                pendingDeletes.remove(userId);
            }
            return;
        }

        List<Schedule> schedules = scheduleService.findAllSchedulesByUserTelegramId(userId);
        if (schedules == null || schedules.isEmpty()) {
            sendMessage(sender, chatId, "У вас нет сохранённых расписаний.");
            return;
        }

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
            } catch (Exception ignored) {}
            sb.append("\n");
        }
        sb.append("\nОтправьте номер расписания (например: 1) чтобы удалить, или /cancel чтобы отменить.");

        pendingDeletes.put(userId, schedules);
        sendMessage(sender, chatId, sb.toString());
    }
}