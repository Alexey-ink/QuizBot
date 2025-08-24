package ru.spbstu.dto;

import java.util.List;

public record QuizDto(
    String question,
    List<String> options,
    int correctOption
) {}
