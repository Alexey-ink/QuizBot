package ru.spbstu.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

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

    @Column(name = "time_zone", nullable = true)
    private String timeZone;

    @Column(name = "login", nullable = true)
    private String login;

    @Column(name = "password_hash", nullable = true)
    private String passwordHash;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "score", nullable = false)
    private Integer score;

    public User() {
        this.role = UserRole.USER;
    }

    public User(Long telegramId, String username, Integer score) {
        this.telegramId = telegramId;
        this.username = username;
        this.role = UserRole.USER;
        this.score = score;
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

    public String getTimeZone() {
        return timeZone;
    }

    public String getLogin() {
        return login;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public Integer getScore() {
        return score;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public void setTelegramId(Long telegramId) {
        this.telegramId = telegramId;
    }
}
