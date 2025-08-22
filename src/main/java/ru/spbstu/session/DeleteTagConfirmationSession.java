package ru.spbstu.session;

import ru.spbstu.session.core.BaseSession;
import ru.spbstu.session.core.SessionType;

public class DeleteTagConfirmationSession extends BaseSession {
    
    public DeleteTagConfirmationSession() {
        super(SessionType.DELETE_TAG_CONFIRMATION);
    }
}
