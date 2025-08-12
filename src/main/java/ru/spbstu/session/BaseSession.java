package ru.spbstu.session;

public abstract class BaseSession implements Session {
    private final SessionType type;

    protected BaseSession(SessionType type) {
        this.type = type;
    }

    @Override
    public SessionType getType() {
        return type;
    }
}
