package ru.spbstu.telegram.session;

import ru.spbstu.telegram.session.core.BaseSession;
import ru.spbstu.telegram.session.core.SessionType;

public class AddTagSession extends BaseSession {
    public AddTagSession() {
        super(SessionType.ADDING_TAG);
    }
}
