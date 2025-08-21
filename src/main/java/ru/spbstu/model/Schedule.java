package ru.spbstu.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "schedules")
public class Schedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    private User user;

    @Column(name = "chat_id", nullable = false)
    private Long chat_id;

    @Column(name = "cron_expression", nullable = false)
    private String cronExpression;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public void setId(Long id) {
        this.id = id;
    }

    public void setChat_id(Long chat_id) {
        this.chat_id = chat_id;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public void setCronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getChat_id() {
        return chat_id;
    }

    public User getUser() {
        return user;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}