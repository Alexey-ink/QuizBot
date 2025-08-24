package ru.spbstu.telegram.session;

import ru.spbstu.telegram.session.core.BaseSession;
import ru.spbstu.telegram.session.core.SessionType;

public class StartSession extends BaseSession {
    public StartSession() {
        super(SessionType.WAITING_TIMEZONE);
    }

}
