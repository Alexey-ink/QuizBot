package ru.spbstu.telegram.handler.schedule;

import org.quartz.SchedulerException;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.spbstu.telegram.handler.CommandHandler;
import ru.spbstu.model.Schedule;
import ru.spbstu.model.User;
import ru.spbstu.repository.UserRepository;
import ru.spbstu.service.ScheduleService;
import ru.spbstu.telegram.sender.MessageSender;
import ru.spbstu.telegram.session.schedule.CreateScheduleSession;
import ru.spbstu.telegram.utils.SessionManager;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class ScheduleCommandHandler extends CommandHandler {
    private final SessionManager sessionManager;
    private final UserRepository userRepository;
    private final ScheduleService scheduleService;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("H:mm");

    public ScheduleCommandHandler(MessageSender messageSender,
                                  SessionManager sessionManager,
                                  UserRepository userRepository,
                                  ScheduleService scheduleService) {
        super(messageSender);
        this.sessionManager = sessionManager;
        this.userRepository = userRepository;
        this.scheduleService = scheduleService;
    }

    @Override
    public String getCommand() {
        return "/schedule";
    }

    private void sendConfirmMessage(CreateScheduleSession session, Long chatId) {
        StringBuilder sb = new StringBuilder("Подтвердите расписание:\n");
        sb.append("Первый вопрос в ").append(session.getFirstTime().toString()).append("\n");
        if (session.getPeriodType() == CreateScheduleSession.PeriodType.DAILY) sb.append("Периодичность: ежедневно\n");
        else if (session.getPeriodType() == CreateScheduleSession.PeriodType.WEEKLY) sb.append("Периодичность: еженедельно, ").append(session.getWeekdays()).append("\n");
        else if (session.getPeriodType() == CreateScheduleSession.PeriodType.HOURLY) sb.append("Периодичность: каждые ").append(session.getIntervalHours()).append(" часов\n");

        sb.append("\nВведите \"Да\" для сохранения или \"Отмена\" для отмены.");
        messageSender.sendMessage(chatId, sb.toString());
    }

    @Override
    public void handle(Update update) {
        Long userId = update.getMessage().getFrom().getId();
        String text = update.getMessage().getText();

        Long chatId = update.getMessage().getChatId();
        CreateScheduleSession session = sessionManager.getOrCreate(userId, CreateScheduleSession.class);

        if (text.equals("/schedule")) {
            session.setStep(CreateScheduleSession.Step.ASK_TIME);
            String answer = "Когда прислать первый вопрос? Введите время в формате HH:mm (например 09:30).";
            messageSender.sendMessage(chatId, answer);
            return;
        }

        switch (session.getStep()) {
            case ASK_TIME -> {
                if (!text.matches("^([01]?[0-9]|2[0-3]):[0-5][0-9]$")) {
                    messageSender.sendMessage(chatId, "Неверный формат времени. " +
                            "Пожалуйста, введите время в формате HH:mm (например 09:30)");
                    return;
                }

                LocalTime t;
                try {
                    t = LocalTime.parse(text, TIME_FMT);
                } catch (Exception ex) {
                    messageSender.sendMessage(chatId, "СТРАННО, не получилось распознать время. Попробуйте, например, 9:30 или 09:30.");
                    return;
                }
                session.setFirstTime(t);
                session.setStep(CreateScheduleSession.Step.ASK_PERIOD_TYPE);
                messageSender.sendMessage(chatId, """
                        Выберите периодичность:\
                        1. Ежедневно\
                        2. Еженедельно\
                        3. Каждые N часов
                        
                        Введите число (1, 2 или 3)""");
            }

            case ASK_PERIOD_TYPE -> {
                try {
                    int num = Integer.parseInt(text.trim());
                    if (num < 1 || num > 3) {
                        messageSender.sendMessage(chatId, "❌ Номер должен быть от 1 до 3.");
                    } else if (num == 1) {
                        session.setPeriodType(CreateScheduleSession.PeriodType.DAILY);
                        session.setStep(CreateScheduleSession.Step.CONFIRM);
                        sendConfirmMessage(session, chatId);
                    } else if (num == 2) {
                        session.setPeriodType(CreateScheduleSession.PeriodType.WEEKLY);
                        session.setStep(CreateScheduleSession.Step.ASK_WEEKDAY);
                        messageSender.sendMessage(chatId, """
                                Выберите день (дни) недели для отправки.
                                Перечислите через запятую (или пробел) дни недели в следующем формате:\
                                
                                ПН, ВТ, СР, ЧТ, ПТ, СБ, ВС""");
                    } else {
                        session.setPeriodType(CreateScheduleSession.PeriodType.HOURLY);
                        session.setStep(CreateScheduleSession.Step.ASK_INTERVAL_HOURS);
                        messageSender.sendMessage(chatId, "Введите положительное целое число интервала в часах (1..24).");
                    }
                } catch (NumberFormatException e) {
                    messageSender.sendMessage(chatId, "❌ Введите число от 1 до 3.");
                }
            }
            case ASK_WEEKDAY -> {
                String normalizedInput = text.trim().toUpperCase();

                if (!normalizedInput.matches("^((ПН|ВТ|СР|ЧТ|ПТ|СБ|ВС)([,\\s]|$))+$")) {
                    messageSender.sendMessage(chatId, """
                            ❌ Неверный формат. Используйте сокращения дней через запятую или пробел:
                            Примеры: `ПН, ВТ, СР` или `ПН ВТ СР`
                            Допустимые дни: ПН, ВТ, СР, ЧТ, ПТ, СБ, ВС""");
                    return;
                }
                String[] dayTokens = normalizedInput.split("[,\\s]+");
                Map<String, DayOfWeek> dayMapping = Map.of(
                        "ПН", DayOfWeek.MONDAY,
                        "ВТ", DayOfWeek.TUESDAY,
                        "СР", DayOfWeek.WEDNESDAY,
                        "ЧТ", DayOfWeek.THURSDAY,
                        "ПТ", DayOfWeek.FRIDAY,
                        "СБ", DayOfWeek.SATURDAY,
                        "ВС", DayOfWeek.SUNDAY
                );

                Set<DayOfWeek> selectedDays = new LinkedHashSet<>();
                for (String token : dayTokens) {
                    DayOfWeek dow = dayMapping.get(token);
                    if (dow != null) selectedDays.add(dow);
                }

                if (selectedDays.isEmpty()) {
                    messageSender.sendMessage(chatId, "❌ Не найдено корректных дней. Попробуйте снова.");
                    return;
                }

                session.setWeekdays(selectedDays);
                session.setStep(CreateScheduleSession.Step.CONFIRM);
                sendConfirmMessage(session, chatId);
            }

            case ASK_INTERVAL_HOURS -> {
                try {
                    int n = Integer.parseInt(text.trim());
                    if (n <= 0 || n > 24) {
                        messageSender.sendMessage(chatId, "Введите положительное целое число интервала в часах (1..24).");
                        return;
                    }
                    session.setIntervalHours(n);
                    session.setStep(CreateScheduleSession.Step.CONFIRM);
                    sendConfirmMessage(session, chatId);
                } catch (NumberFormatException ex) {
                    messageSender.sendMessage(chatId, "Введите положительное целое число интервала в часах.");
                }
            }

            case CONFIRM -> {
                if (text.equalsIgnoreCase("да") || text.equalsIgnoreCase("ok") || text.equalsIgnoreCase("yes")) {
                    Optional<User> ou = userRepository.findByTelegramId(userId);
                    if (ou.isEmpty()) {
                        messageSender.sendMessage(chatId, "Пользователь не найден в базе. Выполните /start.");
                        sessionManager.clearSession(userId);
                        return;
                    }
                    User user = ou.get();

                    String cron;
                    try {
                        cron = buildCronExpression(session);
                    } catch (IllegalArgumentException ex) {
                        messageSender.sendMessage(chatId, "Ошибка при генерации cron: " + ex.getMessage());
                        sessionManager.clearSession(userId);
                        return;
                    }

                    // Сформировать и сохранить Schedule
                    Schedule s = new Schedule();
                    s.setUser(user);
                    s.setChat_id(chatId);
                    s.setCronExpression(cron);
                    s.setCreatedAt(LocalDateTime.now());

                    try {
                        scheduleService.saveAndRegister(s);
                        messageSender.sendMessage(chatId, "✅ Расписание сохранено и зарегистрировано. Cron: " + cron);
                    } catch (SchedulerException e) {
                        e.printStackTrace();
                        messageSender.sendMessage(chatId, "Ошибка при регистрации расписания: " + e.getMessage());
                    }

                    sessionManager.clearSession(userId);
                } else if (text.equalsIgnoreCase("отмена") || text.equalsIgnoreCase("cancel")) {
                    messageSender.sendMessage(chatId, "❌ Сохранение отменено.");
                    sessionManager.clearSession(userId);
                } else {
                    messageSender.sendMessage(chatId, "Введите «Да» для сохранения или «Отмена» для отмены.");
                }
            }

            default -> {
                messageSender.sendMessage(chatId, "Неожиданное состояние сессии. Начните заново командой /schedule");
                sessionManager.clearSession(userId);
            }
        }
    }

    private String buildCronExpression(CreateScheduleSession session) {
        if (session == null) throw new IllegalArgumentException("session == null");

        LocalTime first = session.getFirstTime();
        if (first == null) throw new IllegalArgumentException("First time is not set in session");

        int hour = first.getHour();
        int minute = first.getMinute();

        switch (session.getPeriodType()) {
            case DAILY -> {
                // Каждый день в HH:mm
                // Quartz cron: "sec min hour day-of-month month day-of-week"
                return String.format("0 %d %d * * ?", minute, hour);
            }
            case WEEKLY -> {
                Set<DayOfWeek> days = session.getWeekdays();
                if (days == null || days.isEmpty()) {
                    throw new IllegalArgumentException("Weekdays are not set for WEEKLY period");
                }
                // Преобразуем DayOfWeek -> MON,TUE,...
                // DayOfWeek.name() даёт MONDAY, TUESDAY,... берём первые 3 символа
                StringJoiner sj = new StringJoiner(",");
                for (DayOfWeek dow : days) {
                    String shortName = dow.name().substring(0, 3); // MON, TUE, WED, ...
                    sj.add(shortName);
                }
                String dowList = sj.toString();
                // ? в day-of-month, перечисление в day-of-week
                return String.format("0 %d %d ? * %s", minute, hour, dowList);
            }
            case HOURLY -> {
                Integer interval = session.getIntervalHours();
                if (interval == null || interval <= 0) {
                    throw new IllegalArgumentException("IntervalHours must be set and > 0 for HOURLY period");
                }
                if (interval == 24) {
                    // эквивалент ежедневно
                    return String.format("0 %d %d * * ?", minute, hour);
                }
                // Quartz поддерживает "start/interval" для часов: startHour/interval
                // Это запустит в часы: start, start+interval, start+2*interval, ... (в пределах суток)
                int startHour = hour % 24;
                // валидируем interval <= 24
                if (interval > 24) {
                    throw new IllegalArgumentException("IntervalHours must be <= 24");
                }
                return String.format("0 %d %d/%d * * ?", minute, startHour, interval);
            }
            default -> throw new IllegalArgumentException("Unknown period type: " + session.getPeriodType());
        }
    }

    @Override
    public String getDescription() {
        return "Настройка автоматической отправки вопросов по расписанию";
    }
}
