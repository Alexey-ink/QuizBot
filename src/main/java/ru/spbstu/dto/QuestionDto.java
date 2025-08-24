package ru.spbstu.dto;

import java.util.List;

public record QuestionDto(
        String id,
        String text,
        List<String> options,
        Integer correctOptionInd, //  1-based
        List<String> tags
) {}
