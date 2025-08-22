package ru.spbstu.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.spbstu.model.Schedule;
import java.util.List;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {
    @EntityGraph(attributePaths = {"user"})
    List<Schedule> findAll();

    @EntityGraph(attributePaths = {"user"})
    List<Schedule> findAllByUserTelegramId(Long telegramId);


}
