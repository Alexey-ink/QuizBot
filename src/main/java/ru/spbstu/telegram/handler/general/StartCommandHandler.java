package ru.spbstu.telegram.handler.general;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.spbstu.telegram.handler.CommandHandler;
import ru.spbstu.service.UserService;
import ru.spbstu.telegram.sender.MessageSender;
import ru.spbstu.telegram.session.core.Session;
import ru.spbstu.telegram.session.core.SessionType;
import ru.spbstu.telegram.session.StartSession;
import ru.spbstu.telegram.utils.SessionManager;

import java.util.HashMap;
import java.util.Map;

@Component
public class StartCommandHandler extends CommandHandler {
    private final SessionManager sessionManager;
    private final UserService userService;

    private static final Map<String, String> TIMEZONE_MAP = new HashMap<>();
    static {
        TIMEZONE_MAP.put("МСК-1", "Europe/Kaliningrad");
        TIMEZONE_MAP.put("МСК",   "Europe/Moscow");
        TIMEZONE_MAP.put("МСК+1", "Europe/Samara");
        TIMEZONE_MAP.put("МСК+2", "Asia/Yekaterinburg");
        TIMEZONE_MAP.put("МСК+3", "Asia/Omsk");
        TIMEZONE_MAP.put("МСК+4", "Asia/Krasnoyarsk");
        TIMEZONE_MAP.put("МСК+5", "Asia/Irkutsk");
        TIMEZONE_MAP.put("МСК+6", "Asia/Yakutsk");
        TIMEZONE_MAP.put("МСК+7", "Asia/Vladivostok");
        TIMEZONE_MAP.put("МСК+8", "Asia/Magadan");
        TIMEZONE_MAP.put("МСК+9", "Asia/Kamchatka");
    }

    public StartCommandHandler(SessionManager sessionManager,
                               UserService userService,
                               MessageSender messageSender) {
        super(messageSender);
        this.sessionManager = sessionManager;
        this.userService = userService;
    }

    @Override
    public String getCommand() {
        return "/start";
    }

    @Override
    public String getDescription() {
        return "Запустить бота";
    }

    @Override
    public void handle(Update update) {
        var tgUser = update.getMessage().getFrom();
        Long chatId = update.getMessage().getChatId();
        String userInput = update.getMessage().getText();
        Long telegramId = tgUser.getId();

        logger.info("Обработка команды /start от пользователя {}: {}",
                telegramId, userInput);

        try {
            userService.getOrCreateUser(tgUser.getId(), tgUser.getUserName());

            Session session = sessionManager.getSession(tgUser.getId());

            if (session == null) {
                session = sessionManager.getOrCreate(tgUser.getId(), StartSession.class);
                logger.debug("Создание сессии {} для пользователя {}", session.getType(), telegramId);

                String text = "Добро пожаловать, " +
                        (tgUser.getUserName() != null ? "@" + tgUser.getUserName() : "гость") + "!\n" +
                        "Этот бот поможет тебе создавать вопросы и проходить викторины!\n\n" +
                        "💡 Начни с команды /help, чтобы узнать все возможности\n" +
                        "\uD83C\uDF0D Перед началом выбери свой часовой пояс:\n" +
                        "- МСК-1 (Калининград)\n" +
                        "- МСК   (Москва)\n" +
                        "- МСК+1 (Самара)\n" +
                        "- МСК+2 (Екатеринбург)\n" +
                        "- МСК+3 (Омск)\n" +
                        "- МСК+4 (Красноярск)\n" +
                        "- МСК+5 (Иркутск)\n" +
                        "- МСК+6 (Якутск)\n" +
                        "- МСК+7 (Владивосток)\n" +
                        "- МСК+8 (Магадан)\n" +
                        "- МСК+9 (Камчатка)\n\n" +
                        "👉 Просто введи нужный вариант (например: МСК+4)";

                messageSender.sendPlainMessage(update.getMessage().getChatId(), text);

            } else if (session.getType() == SessionType.WAITING_TIMEZONE) {
                logger.debug("Обработка выбора часового пояса для пользователя {}", telegramId);
                String userInputIgnoreCase = userInput.toUpperCase(java.util.Locale.forLanguageTag("ru"));
                if (TIMEZONE_MAP.containsKey(userInputIgnoreCase)) {
                    String zoneId = TIMEZONE_MAP.get(userInputIgnoreCase);
                    userService.updateUserTimezone(tgUser.getId(), zoneId);

                    messageSender.sendMessage(chatId, "✅ Таймзона сохранена: " + userInputIgnoreCase
                            + " (" + zoneId + ")");
                    sessionManager.clearSession(tgUser.getId());
                } else {
                    messageSender.sendMessage(chatId, "⚠️ Неверный формат. Попробуй ещё раз.\nНапример: МСК+3");
                }
            } else {
                logger.error("Неожиданное состояние сессии для пользователя {}: {}",
                        telegramId, session.getType());
            }
        } catch (Exception e) {
            logger.error("Ошибка при обработке команды /start для пользователя {}: {}",
                    telegramId, e.getMessage(), e);
            messageSender.sendMessage(chatId, "❌ Произошла ошибка при запуске бота");
        }
    }
}
