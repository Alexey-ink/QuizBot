package ru.spbstu.service;

import org.springframework.stereotype.Service;
import ru.spbstu.session.QuestionSession;

import java.util.HashMap;
import java.util.Map;

@Service
public class QuestionSessionService {
    private final Map<Long, QuestionSession> sessions = new HashMap<>();

    public QuestionSession getOrCreateSession(Long userId) {
        return sessions.computeIfAbsent(userId, id -> new QuestionSession());
    }

    public void clearSession(Long userId) {
        sessions.remove(userId);
    }
}