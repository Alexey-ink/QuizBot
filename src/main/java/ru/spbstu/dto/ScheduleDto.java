package ru.spbstu.dto;

public record ScheduleDto(
        Long id,
        Long userId,
        Long chatId, // telegramId
        String cronExpression
) {
    public static ScheduleDto toDto(ru.spbstu.model.Schedule schedule) {
        return new ScheduleDto(
                schedule.getId(),
                schedule.getUser() != null ? schedule.getUser().getId() : null,
                schedule.getChat_id(),
                schedule.getCronExpression()
        );
    }
}