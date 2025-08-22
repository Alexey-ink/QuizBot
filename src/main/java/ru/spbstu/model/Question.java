package ru.spbstu.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Set;

@Entity
@Table(name = "questions")
public class Question {
    private static final SecureRandom random = new SecureRandom();
    private static long lastTimestamp = 0;
    private static int counter = 0;

    @Id
    @Column(length = 25)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String text;

    @Column(name = "correct_option", nullable = false)
    private int correctOption;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private Set<QuestionOption> options;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "question_tag",
            joinColumns = @JoinColumn(name = "question_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags;

    @PrePersist
    public void generateId() {
        long timestamp = System.currentTimeMillis();
        synchronized (Question.class) {
            if (timestamp == lastTimestamp) {
                counter++;
            } else {
                counter = 0;
                lastTimestamp = timestamp;
            }
        }
        int randomPart = random.nextInt(1000);
        this.id = String.format("%d-%03d-%03d", timestamp, counter, randomPart);
    }

    public void setUser(User user) {
        this.user = user;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setCorrectOption(int correctOption) {
        this.correctOption = correctOption;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public void setOptions(Set<QuestionOption> options) {
        this.options = options;
    }

    public void setTags(Set<Tag> tags) {
        this.tags = tags;
    }

    public String getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public User getUser() {
        return user;
    }

    public int getCorrectOption() {
        return correctOption;
    }

    public Set<QuestionOption> getOptions() {
        return options;
    }

    public Set<Tag> getTags() {
        return tags;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
