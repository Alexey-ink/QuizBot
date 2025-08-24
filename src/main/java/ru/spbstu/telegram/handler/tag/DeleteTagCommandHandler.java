package ru.spbstu.telegram.handler.tag;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.spbstu.telegram.handler.CommandHandler;
import ru.spbstu.model.Tag;
import ru.spbstu.repository.TagRepository;
import ru.spbstu.service.UserService;
import ru.spbstu.service.QuestionService;
import ru.spbstu.telegram.sender.MessageSender;
import ru.spbstu.telegram.utils.SessionManager;
import ru.spbstu.telegram.session.DeleteTagConfirmationSession;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Component
public class DeleteTagCommandHandler extends CommandHandler {
    private final TagRepository tagRepository;
    private final UserService userService;
    private final QuestionService questionService;
    private final SessionManager sessionManager;
    
    // Хранилище ожидающих подтверждения удалений тегов
    private final Map<Long, String> pendingTagDeletions = new ConcurrentHashMap<>();

    public DeleteTagCommandHandler(MessageSender messageSender,
                                   TagRepository tagRepository,
                                   UserService userService,
                                   QuestionService questionService,
                                   SessionManager sessionManager) {
        super(messageSender);
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
    public void handle(Update update) {
        String text = update.getMessage().getText();
        String[] parts = text.split(" ", 2);
        
        if (parts.length < 2) {
            messageSender.sendMessage(update.getMessage().getChatId(),
                "❌ Укажите тег.\nИспользование: `/delete_tag <тег>`");
            return;
        }

        String tagName = parts[1].trim();
        Long telegramId = update.getMessage().getFrom().getId();
        
        try {
            var user = userService.getUser(telegramId);
            
            // Проверяем существование тега
            var tagOptional = tagRepository.findByNameIgnoreCase(tagName);
            if (tagOptional.isEmpty()) {
                messageSender.sendMessage(update.getMessage().getChatId(),
                    "❌ Тег #" + messageSender.escapeTagForMarkdown(tagName) + " не существует.");
                return;
            }
            
            Tag tag = tagOptional.get();
            
            // Проверяем владельца тега
            if (!tag.getUser().getId().equals(user.getId())) {
                messageSender.sendMessage(update.getMessage().getChatId(),
                    "❌ Тег #" + messageSender.escapeTagForMarkdown(tagName) + " создан другим пользователем.");
                return;
            }
            
            // Запрашиваем подтверждение
            String confirmationMessage = "❗ Удаление тега #" + messageSender.escapeTagForMarkdown(tagName) + " также удалит вопросы, у которых нет других тегов. Продолжить? (Да/Нет)";
            messageSender.sendMessage(update.getMessage().getChatId(), confirmationMessage);
            
            // Сохраняем ожидающее удаление тега
            pendingTagDeletions.put(telegramId, tagName);
            
            // Создаем сессию для ожидания ответа
            sessionManager.setSession(telegramId, new DeleteTagConfirmationSession());
            
        } catch (Exception e) {
            messageSender.sendMessage(update.getMessage().getChatId(),
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
                        var questions = questionService.getQuestionsByTag(tagName);
                        
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
            sessionManager.clearSession(telegramId);
        }
    }

    public String getPendingTagName(Long telegramId) {
        return pendingTagDeletions.get(telegramId);
    }

    public void removePendingTagDeletion(Long telegramId) {
        pendingTagDeletions.remove(telegramId);
    }

}
