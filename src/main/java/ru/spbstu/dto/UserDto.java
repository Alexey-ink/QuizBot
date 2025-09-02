package ru.spbstu.dto;

import ru.spbstu.model.User;

import java.time.LocalDateTime;

public record UserDto (
    Long user_id,
    Long telegram_id,
    String username,
    String login,
    String passwordHash,
    LocalDateTime created_at,
    String role,
    String time_zone,
    Integer score
){
    public static UserDto toDto(User user) {
        return new UserDto(
                user.getId(),
                user.getTelegramId(),
                user.getUsername(),
                user.getLogin(),
                user.getPasswordHash(),
                user.getCreatedAt(),
                user.getRole().name(),
                user.getTimeZone(),
                user.getScore()
        );
    }
}
