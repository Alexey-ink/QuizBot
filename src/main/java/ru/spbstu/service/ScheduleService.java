package ru.spbstu.service;

import jakarta.annotation.PostConstruct;
import org.springframework.transaction.annotation.Transactional;
import org.quartz.*;
import org.springframework.stereotype.Service;
import ru.spbstu.model.Schedule;
import ru.spbstu.repository.ScheduleRepository;
import ru.spbstu.jobs.SendRandomQuestionJob;

import java.time.ZoneId;
import java.util.TimeZone;

@Service
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final Scheduler scheduler;

    public ScheduleService(ScheduleRepository scheduleRepository, Scheduler scheduler) {
        this.scheduleRepository = scheduleRepository;
        this.scheduler = scheduler;
    }

    @PostConstruct
    public void initSchedules() throws SchedulerException {
        for (Schedule s : scheduleRepository.findAll()) {
            registerSchedule(s);
        }
    }

    /**
     * Сохраняет расписание в БД и регистрирует соответствующий Job/Trigger в Quartz.
     */
    @Transactional
    public void saveAndRegister(Schedule schedule) throws SchedulerException {
        Schedule saved = scheduleRepository.save(schedule);
        registerSchedule(saved);
    }

    /**
     * Регистрирует schedule в quartz. Если job уже существует, обновляет trigger.
     */
    @Transactional
    public void registerSchedule(Schedule s) throws SchedulerException {
        JobDataMap map = new JobDataMap();
        map.put("user_id", s.getUser().getTelegramId());
        map.put("scheduleId", s.getId());
        map.put("chatId", s.getChat_id());

        JobKey jobKey = new JobKey("schedule-job-" + s.getId(), "schedules");
        JobDetail job = JobBuilder.newJob(SendRandomQuestionJob.class)
                .withIdentity(jobKey)
                .usingJobData(map)
                .build();

        // Создаём CronScheduleBuilder, учитывая TZ из Schedule (IANA)
        CronScheduleBuilder cronBuilder = CronScheduleBuilder.cronSchedule(s.getCronExpression());
        if (s.getUser().getTimeZone() != null && !s.getUser().getTimeZone().isBlank()) {
            try {
                ZoneId.of(s.getUser().getTimeZone());
                cronBuilder = cronBuilder.inTimeZone(
                        TimeZone.getTimeZone(ZoneId.of(s.getUser().getTimeZone()))
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        TriggerKey triggerKey = new TriggerKey("schedule-trigger-" + s.getId(), "schedules");
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .withSchedule(cronBuilder)
                .build();

        if (scheduler.checkExists(jobKey)) {
            scheduler.deleteJob(jobKey);
        }

        scheduler.scheduleJob(job, trigger);
    }
}
