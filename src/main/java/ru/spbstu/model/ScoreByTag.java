package ru.spbstu.model;

import jakarta.persistence.*;

@Entity
@Table(name="score_by_tag")
public class ScoreByTag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer score;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "tag_id", nullable = false)
    private Tag tag;

    protected ScoreByTag() {
    }

    public ScoreByTag(Long id, Integer score, User user, Tag tag) {
        this.id = id;
        this.score = score;
        this.user = user;
        this.tag = tag;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public void setTag(Tag tag) {
        this.tag = tag;
    }

    public Long getId() {
        return id;
    }

    public Integer getScore() {
        return score;
    }

    public User getUser() {
        return user;
    }

    public Tag getTag() {
        return tag;
    }
}
