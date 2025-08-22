package ru.spbstu.session;

import ru.spbstu.session.core.BaseSession;
import ru.spbstu.session.core.SessionType;

public class StartSession extends BaseSession {
    public StartSession() {
        super(SessionType.WAITING_TIMEZONE);
    }

}
