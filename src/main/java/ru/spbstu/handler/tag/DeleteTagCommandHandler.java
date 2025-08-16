package ru.spbstu.handler.tag;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.spbstu.handler.CommandHandler;
import ru.spbstu.model.Tag;
import ru.spbstu.repository.TagRepository;
import ru.spbstu.service.UserService;
import ru.spbstu.service.QuestionService;
import ru.spbstu.utils.SessionManager;
import ru.spbstu.session.DeleteTagConfirmationSession;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Component
public class DeleteTagCommandHandler implements CommandHandler {
    private final TagRepository tagRepository;
    private final UserService userService;
    private final QuestionService questionService;
    private final SessionManager sessionManager;
    
    // Хранилище ожидающих подтверждения удалений тегов
    private final Map<Long, String> pendingTagDeletions = new ConcurrentHashMap<>();

    public DeleteTagCommandHandler(TagRepository tagRepository, UserService userService, QuestionService questionService, SessionManager sessionManager) {
        this.tagRepository = tagRepository;
        this.userService = userService;
        this.questionService = questionService;
        this.sessionManager = sessionManager;
    }

    @Override
    public String getCommand() {
        return "/delete_tag";
    }

    @Override
    public String getDescription() {
        return "Удалить тег и связанные с ним вопросы";
    }

    @Override
    public void handle(Update update, AbsSender sender) {
        String text = update.getMessage().getText();
        String[] parts = text.split(" ", 2);
        
        if (parts.length < 2) {
            sendMessage(sender, update.getMessage().getChatId(), 
                "❌ Укажите тег.\nИспользование: /delete_tag <тег>");
            return;
        }

        String tagName = parts[1].trim();
        Long telegramId = update.getMessage().getFrom().getId();
        
        try {
            var user = userService.getUser(telegramId);
            
            // Проверяем существование тега
            var tagOptional = tagRepository.findByUserIdAndNameIgnoreCase(user.getId(), tagName);
            if (tagOptional.isEmpty()) {
                sendMessage(sender, update.getMessage().getChatId(),
                    "❌ Тег «" + tagName + "» не существует.");
                return;
            }
            
            Tag tag = tagOptional.get();
            
            // Проверяем владельца тега
            if (!tag.getUser().getId().equals(user.getId())) {
                sendMessage(sender, update.getMessage().getChatId(),
                    "❌ Тег «" + tagName + "» создан другим пользователем.");
                return;
            }
            
            // Запрашиваем подтверждение
            String confirmationMessage = "❗ Удаление тега «" + tagName + "» также удалит вопросы, у которых нет других тегов. Продолжить? (Да/Нет)";
            sendMessage(sender, update.getMessage().getChatId(), confirmationMessage);
            
            // Сохраняем ожидающее удаление тега
            pendingTagDeletions.put(telegramId, tagName);
            
            // Создаем сессию для ожидания ответа
            sessionManager.setSession(telegramId, new DeleteTagConfirmationSession());
            
        } catch (Exception e) {
            sendMessage(sender, update.getMessage().getChatId(),
                "❌ Ошибка при проверке тега: " + e.getMessage());
        }
    }

    public void confirmDeletion(Long telegramId, boolean confirmed) {
        String tagName = pendingTagDeletions.get(telegramId);
        if (tagName != null) {
            if (confirmed) {
                try {
                    var user = userService.getUser(telegramId);
                    var tagOptional = tagRepository.findByUserIdAndNameIgnoreCase(user.getId(), tagName);
                    if (tagOptional.isPresent()) {
                        Tag tag = tagOptional.get();
                        
                        // Получаем все вопросы с этим тегом
                        var questions = questionService.getQuestionsByTag(telegramId, tagName);
                        
                        // Удаляем тег из всех вопросов, но не удаляем сами вопросы
                        for (var question : questions) {
                            // Убираем тег из вопроса
                            question.getTags().removeIf(t -> t.getId().equals(tag.getId()));
                            // Если у вопроса остались другие теги, обновляем его
                            if (!question.getTags().isEmpty()) {
                                questionService.updateQuestion(question);
                            } else {
                                // Если у вопроса больше нет тегов, удаляем его
                                questionService.deleteQuestion(question.getId());
                            }
                        }
                        
                        // Удаляем сам тег
                        tagRepository.delete(tag);
                    }
                } catch (Exception e) {
                    // Логируем ошибку, но не показываем пользователю
                    e.printStackTrace();
                }
                pendingTagDeletions.remove(telegramId);
            } else {
                pendingTagDeletions.remove(telegramId);
            }
            sessionManager.clear(telegramId);
        }
    }

    public String getPendingTagName(Long telegramId) {
        return pendingTagDeletions.get(telegramId);
    }

    public void removePendingTagDeletion(Long telegramId) {
        pendingTagDeletions.remove(telegramId);
    }

}
