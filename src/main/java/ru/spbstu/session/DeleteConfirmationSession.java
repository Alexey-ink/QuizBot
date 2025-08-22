package ru.spbstu.session;

import ru.spbstu.session.core.BaseSession;
import ru.spbstu.session.core.SessionType;

public class DeleteConfirmationSession extends BaseSession {
    
    public DeleteConfirmationSession() {
        super(SessionType.DELETE_CONFIRMATION);
    }
}
