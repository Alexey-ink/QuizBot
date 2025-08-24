package ru.spbstu.telegram.session;

import ru.spbstu.telegram.session.core.BaseSession;
import ru.spbstu.telegram.session.core.SessionType;

public class DeleteTagConfirmationSession extends BaseSession {
    public DeleteTagConfirmationSession() {
        super(SessionType.DELETE_TAG_CONFIRMATION);
    }
}
