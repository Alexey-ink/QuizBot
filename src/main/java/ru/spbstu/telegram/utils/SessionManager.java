package ru.spbstu.telegram.utils;

import org.springframework.stereotype.Component;
import ru.spbstu.telegram.session.core.Session;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionManager {

    // userId -> session
    private final Map<Long, Session> sessions = new ConcurrentHashMap<>();

    public <T extends Session> T getOrCreate(Long userId, Class<T> clazz) {
        Session session = sessions.computeIfAbsent(userId, id -> createNewSession(clazz));
        return clazz.cast(session);
    }

    private <T extends Session> T createNewSession(Class<T> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create a session for " + clazz.getName(), e);
        }
    }

    public boolean hasSession(Long userId) {
        return sessions.containsKey(userId);
    }

    public Session getSession(Long userId) {
        return sessions.get(userId);
    }

    public <T extends Session> T getSession(Long userId, Class<T> clazz) {
        Session session = sessions.get(userId);
        return session != null ? clazz.cast(session) : null;
    }

    public void clearSession(Long userId) {
        sessions.remove(userId);
    }

    public void setSession(Long userId, Session session) {
        sessions.put(userId, session);
    }
}
