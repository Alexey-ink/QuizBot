package ru.spbstu.dto;

import ru.spbstu.model.Question;

public record QuestionDto(
        String id,
        String text,
        Long userId,
        Long telegramId
) {
    public static QuestionDto toDto(Question question) {
        return new QuestionDto(
                question.getId(),
                question.getText(),
                question.getUser().getId(),
                question.getUser().getTelegramId()
        );
    }
}
