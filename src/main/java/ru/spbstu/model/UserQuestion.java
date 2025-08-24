package ru.spbstu.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
        name = "user_question",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "question_id"})
)
public class UserQuestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "answered_at", nullable = false)
    private Instant answeredAt;

    @Column(name = "is_correct", nullable = false)
    private Boolean correct;

    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Question getQuestion() { return question; }
    public void setQuestion(Question question) { this.question = question; }
    public Instant getAnsweredAt() { return answeredAt; }
    public void setAnsweredAt(Instant answeredAt) { this.answeredAt = answeredAt; }
    public Boolean getCorrect() { return correct; }
    public void setCorrect(Boolean correct) { this.correct = correct; }
}