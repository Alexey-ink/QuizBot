package ru.spbstu.telegram.handler.schedule;

import org.quartz.SchedulerException;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.spbstu.telegram.handler.CommandHandler;
import ru.spbstu.telegram.sender.MessageSender;
import ru.spbstu.telegram.session.schedule.CreateScheduleSession;
import ru.spbstu.telegram.utils.SessionManager;
import ru.spbstu.service.ScheduleService;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class ScheduleCommandHandler extends CommandHandler {
    private final SessionManager sessionManager;
    private final ScheduleService scheduleService;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("H:mm");

    public ScheduleCommandHandler(MessageSender messageSender,
                                  SessionManager sessionManager,
                                  ScheduleService scheduleService) {
        super(messageSender);
        this.sessionManager = sessionManager;
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
        else if (session.getPeriodType() == CreateScheduleSession.PeriodType.WEEKLY) {
            sb.append("Периодичность: еженедельно, ").append(session.getWeekdays()).append("\n");
        }
        else if (session.getPeriodType() == CreateScheduleSession.PeriodType.HOURLY) {
            sb.append("Периодичность: каждые ").append(session.getIntervalHours()).append(" часов\n");
        }

        sb.append("\nВведите \"Да\" для сохранения или \"Отмена\" для отмены.");
        messageSender.sendMessage(chatId, sb.toString());
    }

    @Override
    public void handle(Update update) {
        Long telegramId = update.getMessage().getFrom().getId();
        String text = update.getMessage().getText();

        logger.info("Обработка команды /schedule от пользователя {}: {}", telegramId, text);

        Long chatId = update.getMessage().getChatId();

        try {
            CreateScheduleSession session = sessionManager.getOrCreate(telegramId, CreateScheduleSession.class);

            if (text.equals("/schedule")) {
                session.setStep(CreateScheduleSession.Step.ASK_TIME);
                String answer = "Когда прислать первый вопрос? Введите время в формате HH:mm (например 09:30).";
                messageSender.sendMessage(chatId, answer);
                logger.debug("Начало создания расписания пользователем {}", telegramId);
                return;
            }

            switch (session.getStep()) {
                case ASK_TIME -> {
                    logger.debug("Обработка времени для расписания пользователем {}: {}", telegramId, text);
                    if (!text.matches("^([01]?[0-9]|2[0-3]):[0-5][0-9]$")) {
                        messageSender.sendMessage(chatId, "Неверный формат времени. " +
                                "Пожалуйста, введите время в формате HH:mm (например 09:30)");
                        return;
                    }

                    LocalTime t;
                    try {
                        t = LocalTime.parse(text, TIME_FMT);
                    } catch (Exception ex) {
                        messageSender.sendMessage(chatId, "СТРАННО, не получилось распознать время. " +
                                "Попробуйте, например, 9:30 или 09:30.");
                        return;
                    }
                    session.setFirstTime(t);
                    session.setStep(CreateScheduleSession.Step.ASK_PERIOD_TYPE);
                    logger.debug("Время {} установлено для пользователя {}", t, telegramId);
                    messageSender.sendMessage(chatId,
                            "Выберите периодичность:\n" +
                            "1. Ежедневно\n" +
                            "2. Еженедельно\n" +
                            "3. Каждые N часов\n" +
                            "Введите число (1, 2 или 3)");
                }

                case ASK_PERIOD_TYPE -> {
                    logger.debug("Обработка типа периодичности пользователем {}: {}", telegramId, text);
                    try {
                        int num = Integer.parseInt(text.trim());
                        if (num < 1 || num > 3) {
                            messageSender.sendMessage(chatId, "❌ Номер должен быть от 1 до 3.");
                        } else if (num == 1) {
                            session.setPeriodType(CreateScheduleSession.PeriodType.DAILY);
                            session.setStep(CreateScheduleSession.Step.CONFIRM);
                            logger.debug("Выбрана ежедневная периодичность пользователем {}", telegramId);
                            sendConfirmMessage(session, chatId);
                        } else if (num == 2) {
                            session.setPeriodType(CreateScheduleSession.PeriodType.WEEKLY);
                            session.setStep(CreateScheduleSession.Step.ASK_WEEKDAY);
                            logger.debug("Выбрана еженедельная периодичность пользователем {}", telegramId);
                            messageSender.sendMessage(chatId, """
                                    Выберите день (дни) недели для отправки.
                                    Перечислите через запятую (или пробел) дни недели в следующем формате:\
                                    
                                    ПН, ВТ, СР, ЧТ, ПТ, СБ, ВС""");
                        } else {
                            session.setPeriodType(CreateScheduleSession.PeriodType.HOURLY);
                            session.setStep(CreateScheduleSession.Step.ASK_INTERVAL_HOURS);
                            logger.debug("Выбрана почасовая периодичность пользователем {}", telegramId);
                            messageSender.sendMessage(chatId, "Введите положительное целое число интервала в часах (1..24).");
                        }
                    } catch (NumberFormatException e) {
                        logger.warn("Неверный формат номера периодичности от пользователя {}: {}", telegramId, text);
                        messageSender.sendMessage(chatId, "❌ Введите число от 1 до 3.");
                    }
                }
                case ASK_WEEKDAY -> {
                    logger.debug("Обработка дней недели пользователем {}: {}", telegramId, text);
                    String normalizedInput = text.trim().toUpperCase();

                    if (!normalizedInput.matches("^((ПН|ВТ|СР|ЧТ|ПТ|СБ|ВС)[,\\s]*)+$")) {
                        logger.warn("Неверный формат дней недели от пользователя {}: {}", telegramId, text);
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
                    logger.debug("Установлены дни недели для пользователя {}: {}", telegramId, selectedDays);
                    sendConfirmMessage(session, chatId);
                }

                case ASK_INTERVAL_HOURS -> {
                    logger.debug("Обработка интервала часов пользователем {}: {}", telegramId, text);
                    try {
                        int n = Integer.parseInt(text.trim());
                        if (n <= 0 || n > 24) {
                            messageSender.sendMessage(chatId, "Введите положительное целое число интервала в часах (1..24).");
                            return;
                        }
                        session.setIntervalHours(n);
                        session.setStep(CreateScheduleSession.Step.CONFIRM);
                        logger.debug("Установлен интервал часов для пользователя {}: {}", telegramId, n);
                        sendConfirmMessage(session, chatId);
                    } catch (NumberFormatException ex) {
                        messageSender.sendMessage(chatId, "Введите положительное целое число интервала в часах.");
                    }
                }

                case CONFIRM -> {
                    logger.debug("Обработка подтверждения пользователем {}: {}", telegramId, text);
                    if (text.equalsIgnoreCase("да") || text.equalsIgnoreCase("ok")
                            || text.equalsIgnoreCase("yes")) {

                        String cron;
                        try {
                            cron = buildCronExpression(session);
                            logger.debug("Сгенерирован cron expression для пользователя {}: {}", telegramId, cron);
                        } catch (IllegalArgumentException ex) {
                            logger.error("Ошибка генерации cron для пользователя {}: {}", telegramId, ex.getMessage());
                            messageSender.sendMessage(chatId, "Ошибка при генерации cron: " + ex.getMessage());
                            sessionManager.clearSession(telegramId);
                            return;
                        }

                        try {
                            scheduleService.saveAndRegisterSchedule(telegramId, cron);
                            messageSender.sendPlainMessage(chatId,
                                    "✅ Расписание сохранено и зарегистрировано.\n" +
                                            "Cron: " + cron);
                            logger.info("Расписание создано пользователем {}: {}", telegramId, cron);
                        } catch (SchedulerException e) {
                            logger.error("Ошибка регистрации расписания пользователем {}: {}", telegramId, e.getMessage(), e);
                            e.printStackTrace();
                            messageSender.sendMessage(chatId,
                                    "Ошибка при регистрации расписания: " + e.getMessage());
                        }

                        sessionManager.clearSession(telegramId);
                    } else if (text.equalsIgnoreCase("отмена") || text.equalsIgnoreCase("cancel")) {
                        messageSender.sendMessage(chatId, "❌ Сохранение отменено.");
                        logger.info("Создание расписания отменено пользователем {}", telegramId);
                        sessionManager.clearSession(telegramId);
                    } else {
                        logger.warn("Неверный ответ подтверждения от пользователя {}: {}", telegramId, text);
                        messageSender.sendMessage(chatId, "Введите «Да» для сохранения или «Отмена» для отмены.");
                    }
                }

                default -> {
                    logger.warn("Неожиданное состояние сессии пользователя {}: {}", telegramId, session.getStep());
                    messageSender.sendMessage(chatId,
                            "Неожиданное состояние сессии. Начните заново командой /schedule");
                    sessionManager.clearSession(telegramId);
                }
            }
        } catch (Exception e) {
            logger.error("Ошибка при обработке команды /schedule пользователем {}: {}", telegramId, e.getMessage(), e);
            messageSender.sendMessage(chatId, "❌ Произошла ошибка при настройке расписания");
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
                int startHour = hour % 24;
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
