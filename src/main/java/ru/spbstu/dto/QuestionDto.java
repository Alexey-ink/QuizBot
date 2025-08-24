package ru.spbstu.dto;

import ru.spbstu.model.Question;
import java.util.List;
import java.util.stream.Collectors;

public record QuestionDto(
        String id,
        String text,
        Long userId,
        Long telegramId,
        int correctOptionInd, //  1-based
        List<OptionDto> options
) {
    public static QuestionDto toDto(Question question) {
        return new QuestionDto(
                question.getId(),
                question.getText(),
                question.getUser().getId(),
                question.getUser().getTelegramId(),
                question.getCorrectOption(),
                question.getOptions().stream()
                        .map(OptionDto::toDto)
                        .collect(Collectors.toList())
        );
    }
}
