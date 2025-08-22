package ru.spbstu.session;

import ru.spbstu.session.core.BaseSession;
import ru.spbstu.session.core.SessionType;

import java.util.ArrayList;
import java.util.List;

public class AddQuestionSession extends BaseSession {

    public enum Step {
        ASK_QUESTION_TEXT,
        ASK_ANSWER_OPTIONS,
        ASK_CORRECT_OPTION,
        ASK_TAGS,
        FINISHED
    }

    public AddQuestionSession() {
        super(SessionType.QUESTION);
    }

    private Step step = Step.ASK_QUESTION_TEXT;
    private String questionText;
    private final List<String> options = new ArrayList<>();
    private int correctOption;
    private List<String> tags = new ArrayList<>();

    public Step getStep() {
        return step;
    }

    public void setStep(Step step) {
        this.step = step;
    }

    public String getQuestionText() {
        return questionText;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    public List<String> getOptions() {
        return options;
    }

    public int getCorrectOption() {
        return correctOption;
    }

    public void setCorrectOption(int correctOption) {
        this.correctOption = correctOption;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }
}