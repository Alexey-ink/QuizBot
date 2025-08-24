package ru.spbstu.telegram.session.schedule;

import ru.spbstu.telegram.session.core.BaseSession;
import ru.spbstu.telegram.session.core.SessionType;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

public class CreateScheduleSession extends BaseSession {
    public enum Step { ASK_TIME, ASK_PERIOD_TYPE, ASK_WEEKDAY, ASK_INTERVAL_HOURS, CONFIRM }
    public enum PeriodType { DAILY, WEEKLY, HOURLY }

    private Step step = Step.ASK_TIME;
    private LocalTime firstTime;
    private PeriodType periodType;
    private Set<DayOfWeek> weekday;
    private Integer intervalHours;

    public CreateScheduleSession() {
        super(SessionType.CREATING_SCHEDULE);
    }

    public Step getStep() { return step; }
    public void setStep(Step step) { this.step = step; }

    public LocalTime getFirstTime() { return firstTime; }
    public void setFirstTime(LocalTime firstTime) { this.firstTime = firstTime; }

    public PeriodType getPeriodType() { return periodType; }
    public void setPeriodType(PeriodType periodType) { this.periodType = periodType; }

    public void setWeekdays(Set<DayOfWeek> weekday) {
        this.weekday = weekday;
    }

    public Set<DayOfWeek> getWeekdays() {
        return weekday;
    }

    public Integer getIntervalHours() { return intervalHours; }
    public void setIntervalHours(Integer intervalHours) { this.intervalHours = intervalHours; }
}

