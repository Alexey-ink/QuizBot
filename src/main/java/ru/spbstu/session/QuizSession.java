package ru.spbstu.session;

import ru.spbstu.model.Question;

import java.time.Instant;

public class QuizSession extends BaseSession {
    private Question currentQuestion;
    private Instant questionStartTime;
    private boolean answered = false;
    private Step step = Step.WAITING_FOR_ANSWER;
    
    public enum Step {
        WAITING_FOR_ANSWER,
        FINISHED
    }
    
    public QuizSession() {
        super(SessionType.QUIZ);
    }
    
    public Question getCurrentQuestion() {
        return currentQuestion;
    }
    
    public void setCurrentQuestion(Question currentQuestion) {
        this.currentQuestion = currentQuestion;
        this.questionStartTime = Instant.now();
        this.answered = false;
    }
    
    public Instant getQuestionStartTime() {
        return questionStartTime;
    }
    
    public boolean isAnswered() {
        return answered;
    }
    
    public void setAnswered(boolean answered) {
        this.answered = answered;
    }

    public Step getStep() {
        return step;
    }
    
    public void setStep(Step step) {
        this.step = step;
    }
    
    public boolean isTimeExpired() {
        if (questionStartTime == null) return false;
        return Instant.now().isAfter(questionStartTime.plusSeconds(30));
    }
}
