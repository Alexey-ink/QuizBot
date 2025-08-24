package ru.spbstu.dto;

import ru.spbstu.model.Tag;

import java.util.Set;
import java.util.stream.Collectors;

public record TagDto (
        Long id,
        String name,
        Long userId,
        Long telegramId,
        Set<QuestionDto> questions
) {
    public static TagDto toDto(Tag tag) {
        return new TagDto(
                tag.getId(),
                tag.getName(),
                tag.getUser().getId(),
                tag.getUser().getTelegramId(),
                tag.getQuestions().stream()
                        .map(QuestionDto::toDto)
                        .collect(Collectors.toSet())
        );
    }
}
