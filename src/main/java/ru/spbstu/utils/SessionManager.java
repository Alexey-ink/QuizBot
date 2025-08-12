package ru.spbstu.utils;

import org.springframework.stereotype.Component;
import ru.spbstu.session.Session;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionManager {

    // userId -> session
    private final Map<Long, Session> sessions = new ConcurrentHashMap<>();

    public <T extends Session> T getOrCreate(Long userId, Class<T> clazz) {
        return (T) sessions.computeIfAbsent(userId, id -> {
            try {
                return clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Не удалось создать сессию для " + clazz.getName(), e);
            }
        });
    }

    public boolean hasSession(Long userId) {
        return sessions.containsKey(userId);
    }

    public Session getSession(Long userId) {
        return sessions.get(userId);
    }

    public void clear(Long userId) {
        sessions.remove(userId);
    }

    public void setSession(Long userId, Session session) {
        sessions.put(userId, session);
    }
}
