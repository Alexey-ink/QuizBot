package ru.spbstu.handler.schedule;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.bots.AbsSender;
import ru.spbstu.handler.CommandHandler;
import ru.spbstu.session.ScheduleCreationSession;
import ru.spbstu.utils.SessionManager;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class ScheduleCommandHandler implements CommandHandler {
    private final SessionManager sessionManager;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("H:mm");

    public ScheduleCommandHandler(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public String getCommand() {
        return "/schedule";
    }

    @Override
    public void handle(Update update, AbsSender sender) {
        Long userId = update.getMessage().getFrom().getId();
        String text = update.getMessage().getText();

        Long chatId = update.getMessage().getChatId();
        ScheduleCreationSession session = sessionManager.getOrCreate(userId, ScheduleCreationSession.class);

        if (text.equals("/schedule")) {
            session.setStep(ScheduleCreationSession.Step.ASK_TIME);
            String answer = "Когда прислать первый вопрос? Введите время в формате HH:mm (например 09:30).";
            sendMessage(sender, chatId, answer);
            return;
        }

        switch (session.getStep()) {
            case ASK_TIME -> {
                if (!text.matches("^([01]?[0-9]|2[0-3]):[0-5][0-9]$")) {
                    sendMessage(sender, chatId, "Неверный формат времени. " +
                            "Пожалуйста, введите время в формате HH:mm (например 09:30)");
                    return;
                }

                LocalTime t;
                try {
                    t = LocalTime.parse(text, TIME_FMT);
                } catch (Exception ex) {
                    sendMessage(sender, chatId, "СТРАННО, не получилось распознать время. Попробуйте, например, 9:30 или 09:30.");
                    return;
                }
                session.setFirstTime(t);
                session.setStep(ScheduleCreationSession.Step.ASK_PERIOD_TYPE);
                sendMessage(sender, chatId, "Выберите периодичность:\n" +
                        "1. Ежедневно" +
                        "2. Еженедельно" +
                        "3. Каждые N часов\n\nВведите число (1, 2 или 3)");
            }

            case ASK_PERIOD_TYPE -> {
                try {
                    int num = Integer.parseInt(text.trim());
                    if (num < 1 || num > 3) {
                        sendMessage(sender, chatId, "❌ Номер должен быть от 1 до 3.");
                    } else if (num == 1) {
                        session.setPeriodType(ScheduleCreationSession.PeriodType.DAILY);
                        session.setStep(ScheduleCreationSession.Step.CONFIRM);
                        sendConfirmMessage(session, sender, chatId);
                    } else if (num == 2) {
                        session.setPeriodType(ScheduleCreationSession.PeriodType.WEEKLY);
                        session.setStep(ScheduleCreationSession.Step.ASK_WEEKDAY);
                        sendMessage(sender, chatId, "Выберите день (дни) недели для отправки\n" +
                                "Перечислите через запятую (или пробел) дни недели в следующем формате:" +
                                "\nПН, ВТ, СР, ЧТ, ПТ, СБ, ВС");
                    } else {
                        session.setPeriodType(ScheduleCreationSession.PeriodType.HOURLY);
                        session.setStep(ScheduleCreationSession.Step.ASK_INTERVAL_HOURS);
                        sendMessage(sender, chatId, "Введите положительное целое число интервала в часах (от 0 до 24)");
                    }
                } catch (NumberFormatException e) {
                    sendMessage(sender, chatId, "❌ Введите число от 1 до 3.");
                }
            }
            case ASK_WEEKDAY -> {
                String normalizedInput = text.trim().toUpperCase();

                if (!normalizedInput.matches("^((ПН|ВТ|СР|ЧТ|ПТ|СБ|ВС)([,\\s]|$))+$")) {
                    sendMessage(sender, chatId, "❌ Неверный формат. Используйте сокращения дней через запятую или пробел:\n" +
                            "Примеры: `ПН, ВТ, СР` или `ПН ВТ СР`\n" +
                            "Допустимые дни: ПН, ВТ, СР, ЧТ, ПТ, СБ, ВС");
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
                    sendMessage(sender, chatId, "❌ Не найдено корректных дней. Попробуйте снова.");
                    return;
                }

                session.setWeekdays(selectedDays);
                session.setStep(ScheduleCreationSession.Step.CONFIRM);
                sendConfirmMessage(session, sender, chatId);
            }

            case ASK_INTERVAL_HOURS -> {
                try {
                    int n = Integer.parseInt(text);
                    if (n <= 0) throw new NumberFormatException();
                    session.setIntervalHours(n);
                    session.setStep(ScheduleCreationSession.Step.CONFIRM);
                    sendConfirmMessage(session, sender, chatId);
                } catch (NumberFormatException ex) {
                    sendMessage(sender, chatId, "Введите положительное целое число интервала в часах.");
                }
            }
        }
    }

    private void sendConfirmMessage(ScheduleCreationSession session, AbsSender sender, Long chatId) {
        StringBuilder sb = new StringBuilder("Подтвердите расписание:\n");
        sb.append("Первый вопрос в ").append(session.getFirstTime().toString()).append("\n");
        if (session.getPeriodType() == ScheduleCreationSession.PeriodType.DAILY) sb.append("Периодичность: ежедневно\n");
        else if (session.getPeriodType() == ScheduleCreationSession.PeriodType.WEEKLY) sb.append("Периодичность: еженедельно, ").append(session.getWeekdays()).append("\n");
        else if (session.getPeriodType() == ScheduleCreationSession.PeriodType.HOURLY) sb.append("Периодичность: каждые ").append(session.getIntervalHours()).append(" часов\n");

        sb.append("\nВведите \"Да\" для сохранения или \"Отмена\" для отмены.");
        sendMessage(sender, chatId, sb.toString());
    }

    @Override
    public String getDescription() {
        return "ЫВофрпаыдавоыа";
    }
}
