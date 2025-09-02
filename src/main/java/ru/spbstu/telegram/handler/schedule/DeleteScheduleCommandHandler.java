package ru.spbstu.telegram.handler.schedule;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.spbstu.dto.ScheduleDto;
import ru.spbstu.telegram.handler.CommandHandler;
import ru.spbstu.telegram.sender.MessageSender;
import ru.spbstu.telegram.session.schedule.DeleteScheduleSession;
import ru.spbstu.telegram.utils.SessionManager;
import ru.spbstu.service.ScheduleService;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DeleteScheduleCommandHandler extends CommandHandler {

    private final ScheduleService scheduleService;
    private final SessionManager sessionManager;
    private final Map<Long, List<ScheduleDto>> pendingDeletes = new ConcurrentHashMap<>();

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
                List<ScheduleDto> list = pendingDeletes.get(telegramId);
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

                ScheduleDto chosen = list.get(choice - 1);
                try {
                    scheduleService.deleteSchedule(chosen.id());
                    sessionManager.clearSession(telegramId);
                    messageSender.sendMessage(chatId, "Расписание с id=" + chosen.id() + " удалено.");
                } catch (Exception ex) {
                    ex.printStackTrace();
                    messageSender.sendMessage(chatId, "Не удалось удалить расписание: " + ex.getMessage());
                } finally {
                    pendingDeletes.remove(telegramId);
                }
                return;
            }

            List<ScheduleDto> schedules = scheduleService.findAllSchedulesByUserTelegramId(telegramId);
            if (schedules == null || schedules.isEmpty()) {
                logger.info("У пользователя {} нет расписаний для удаления", telegramId);
                messageSender.sendMessage(chatId, "У вас нет сохранённых расписаний.");
                return;
            }

            logger.debug("Найдено {} расписаний для пользователя {}", schedules.size(), telegramId);

            StringBuilder sb = new StringBuilder();
            sb.append("Ваши расписания:\n\n");
            for (int i = 0; i < schedules.size(); i++) {
                ScheduleDto s = schedules.get(i);
                sb.append(i + 1).append(") ");
                try {
                    if (s.cronExpression() != null) {
                        sb.append(cronToReadable(s));
                    }
                } catch (Exception ignored) {
                }
                sb.append("\n");
            }
            sb.append("\nОтправьте номер расписания (например: 1) чтобы удалить, или /cancel чтобы отменить.");

            pendingDeletes.put(telegramId, schedules);
            messageSender.sendPlainMessage(chatId, sb.toString());
            logger.info("Список расписаний отправлен пользователю {} для выбора удаления", telegramId);
        } catch (Exception e) {
            logger.error("Ошибка при обработке команды /unschedule пользователем {}: {}",
                    telegramId, e.getMessage(), e);
            messageSender.sendMessage(chatId, "❌ Произошла ошибка при обработке команды");
        }

    }

    private String cronToReadable(ScheduleDto s) {
        String cron = s.cronExpression();
        if (cron == null || cron.isEmpty()) return "Неизвестно";

        String[] parts = cron.split(" ");
        if (parts.length != 6) return cron; // стандартный Quartz cron: sec min hour day month day-of-week

        String min = parts[1];
        String hour = parts[2];
        String dayOfMonth = parts[3];
        String month = parts[4];
        String dayOfWeek = parts[5];

        if ("*".equals(dayOfMonth) && "*".equals(month)
                && "?".equals(dayOfWeek) && !hour.contains("/")) {
            return String.format("ежедневно в %s:%s", hour, min);
        }

        if ("?".equals(dayOfMonth) && "*".equals(month) && !"?".equals(dayOfWeek)) {
            // еженедельно
            String days = Arrays.stream(dayOfWeek.split(","))
                    .map(this::quartzDayToRussian)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            return String.format("еженедельно (%s) в %s:%s", days, hour, min);
        }

        if (hour.contains("/")) {
            String[] hParts = hour.split("/");
            return String.format("каждые %s часов начиная с %s:%s", hParts[1], hParts[0], min);
        }

        return cron;
    }

    private String quartzDayToRussian(String day) {
        return switch (day.toUpperCase()) {
            case "MON" -> "ПН";
            case "TUE" -> "ВТ";
            case "WED" -> "СР";
            case "THU" -> "ЧТ";
            case "FRI" -> "ПТ";
            case "SAT" -> "СБ";
            case "SUN" -> "ВС";
            default -> day;
        };
    }
}