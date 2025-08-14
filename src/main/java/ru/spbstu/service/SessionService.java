package ru.spbstu.service;

import org.springframework.stereotype.Service;
import ru.spbstu.session.Session;
import ru.spbstu.session.SessionType;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
public class SessionService {
    private final Map<Long, Map<SessionType, Session>> sessions = new ConcurrentHashMap<>();

    public <T extends Session> T getOrCreateSession(Long userId, SessionType type, Supplier<T> creator) {
        Map<SessionType, Session> userSessions = sessions.computeIfAbsent(userId, id -> new ConcurrentHashMap<>());
        Session session = userSessions.computeIfAbsent(type, t -> creator.get());

        @SuppressWarnings("unchecked")
        T result = (T) session;
        return result;
    }

    public <T extends Session> Optional<T> getSession(Long userId, SessionType type, Class<T> clazz) {
        return Optional.ofNullable(sessions.get(userId))
                .map(userSessions -> userSessions.get(type))
                .map(clazz::cast);
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

