package ru.spbstu.dto;


import ru.spbstu.model.QuestionOption;

public record OptionDto (
    Long id,
    String text,
    int optionNumber
) {
    public static OptionDto toDto(QuestionOption option) {
        return new OptionDto(
                option.getId(),
                option.getText(),
                option.getOptionNumber()
        );
    }
}
