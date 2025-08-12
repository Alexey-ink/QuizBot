package ru.spbstu.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_id", nullable = false, unique = true)
    private Long telegramId;

    @Column(nullable = true, unique = true)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column(name = "password_hash", nullable = true)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public User() {
        this.role = UserRole.USER;
    }

    public User(Long telegramId, String username) {
        this.telegramId = telegramId;
        this.username = username;
        this.role = UserRole.USER;
        this.createdAt = LocalDateTime.now();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getTelegramId() {
        return telegramId;
    }

    public String getUsername() {
        return username;
    }

    public UserRole getRole() {
        return role;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

}
