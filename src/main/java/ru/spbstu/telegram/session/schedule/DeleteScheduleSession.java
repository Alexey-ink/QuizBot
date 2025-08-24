package ru.spbstu.telegram.session.schedule;

import ru.spbstu.telegram.session.core.BaseSession;
import ru.spbstu.telegram.session.core.SessionType;

public class DeleteScheduleSession extends BaseSession {
    public DeleteScheduleSession() {
        super(SessionType.DELETING_SCHEDULE);
    }
}
