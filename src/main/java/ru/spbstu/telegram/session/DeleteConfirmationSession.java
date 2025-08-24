package ru.spbstu.telegram.session;

import ru.spbstu.telegram.session.core.BaseSession;
import ru.spbstu.telegram.session.core.SessionType;

public class DeleteConfirmationSession extends BaseSession {
    public DeleteConfirmationSession() {
        super(SessionType.DELETE_CONFIRMATION);
    }
}
