package ru.spbstu.session.schedule;

import ru.spbstu.session.core.BaseSession;
import ru.spbstu.session.core.SessionType;

public class DeleteScheduleSession extends BaseSession {
    public DeleteScheduleSession() {
        super(SessionType.DELETING_SCHEDULE);
    }
}
