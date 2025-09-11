package ru.spbstu.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.spbstu.dto.UserDto;
import ru.spbstu.service.UserService;
import ru.spbstu.telegram.sender.MessageSender;

import java.util.List;

@RestController
@RequestMapping("/admin/users/send-broadcast")
public class BroadcastController {
    private final UserService userService;
    private final MessageSender messageSender;

    public BroadcastController(UserService userService, MessageSender messageSender) {
        this.userService = userService;
        this.messageSender = messageSender;
    }

    private final static String UPDATE_MESSAGE = """
        *📢 Обновление QuizBot v1.0.1* 🎉

        В этой версии мы сделали несколько улучшений и исправлений:

        • 🛠 Исправлена ошибка в команде `/show_quiz_by_tag` при выводе вопросов со специальными символами
        • ⏱ Убран таймаут для вопросов — теперь можно отвечать в удобном темпе
        • 🐞 Исправлена ошибка при отправке новых команд, когда предыдущий вопрос ещё не был завершён

        💡 *Обратная связь:*
        Если у вас есть предложения или вы нашли ошибку — напишите @AlexeyShihalev.
        """;

    @PostMapping("/update")
    public ResponseEntity<String> sendUpdateBroadcast() {

        List<UserDto> users = userService.findAll();

        for (UserDto user : users) {
            try {
                messageSender.sendMessage(user.telegram_id(), UPDATE_MESSAGE);
            } catch (Exception e) {
                System.err.println("Не удалось отправить сообщение пользователю " + user.telegram_id());
            }
        }

        return ResponseEntity.ok("Broadcast отправлен " + users.size() + " пользователям");
    }

    @PostMapping("/update/{telegramId}")
    public ResponseEntity<String> sendUpdateToUser(@PathVariable("telegramId") Long telegramId) {
        try {
            messageSender.sendMessage(telegramId, UPDATE_MESSAGE);
            return ResponseEntity.ok("Сообщение отправлено пользователю " + telegramId);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Ошибка отправки сообщения пользователю " + telegramId + ": " + e.getMessage());
        }
    }
}
