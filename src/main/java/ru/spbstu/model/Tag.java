package ru.spbstu.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.Set;

@Entity
@Table(name = "tags", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "name"})
})
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String name;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToMany(mappedBy = "tags")
    private Set<Question> questions;

    public void setId(Long id) {
        this.id = id;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public void setQuestions(Set<Question> questions) {
        this.questions = questions;
    }

    public String getName() {
        return name;
    }

    // Геттеры
    public Long getId() {
        return id;
    }
    
    public User getUser() {
        return user;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public Set<Question> getQuestions() {
        return questions;
    }
}
