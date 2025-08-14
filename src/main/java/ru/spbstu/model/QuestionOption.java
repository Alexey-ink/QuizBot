package ru.spbstu.model;

import jakarta.persistence.*;

@Entity
@Table(name = "question_options", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"question_id", "number"})
})
public class QuestionOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "number", nullable = false)
    private int optionNumber;

    @Column(length = 200, nullable = false)
    private String text;

    public void setId(Long id) {
        this.id = id;
    }

    public void setQuestion(Question question) {
        this.question = question;
    }

    public void setOptionNumber(int optionNumber) {
        this.optionNumber = optionNumber;
    }

    public void setText(String text) {
        this.text = text;
    }

    public int getOptionNumber() {
        return optionNumber;
    }

    public Long getId() {
        return id;
    }
    
    public Question getQuestion() {
        return question;
    }

    public String getText() {
        return text;
    }
}
