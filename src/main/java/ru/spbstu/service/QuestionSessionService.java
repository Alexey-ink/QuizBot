package ru.spbstu.service;

import org.springframework.stereotype.Service;
import ru.spbstu.session.AddQuestionSession;

import java.util.HashMap;
import java.util.Map;

@Service
public class QuestionSessionService {
    private final Map<Long, AddQuestionSession> sessions = new HashMap<>();

    public AddQuestionSession getOrCreateSession(Long userId) {
        return sessions.computeIfAbsent(userId, id -> new AddQuestionSession());
    }

    public void clearSession(Long userId) {
        sessions.remove(userId);
    }
}