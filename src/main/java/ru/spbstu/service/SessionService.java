package ru.spbstu.service;

import org.springframework.stereotype.Service;
import ru.spbstu.session.Session;
import ru.spbstu.session.SessionType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
public class SessionService {
    private final Map<Long, Map<SessionType, Session>> sessions = new ConcurrentHashMap<>();

    public <T extends Session> T getOrCreateSession(Long userId, SessionType type, Supplier<T> creator) {
        return (T) sessions
                .computeIfAbsent(userId, id -> new ConcurrentHashMap<>())
                .computeIfAbsent(type, t -> creator.get());
    }

    public <T extends Session> T getSession(Long userId, SessionType type) {
        return (T) sessions.getOrDefault(userId, Map.of()).get(type);
    }

    public void clearSession(Long userId, SessionType type) {
        Map<SessionType, Session> userSessions = sessions.get(userId);
        if (userSessions != null) {
            userSessions.remove(type);
            if (userSessions.isEmpty()) {
                sessions.remove(userId);
            }
        }
    }
}

