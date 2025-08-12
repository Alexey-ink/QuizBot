package ru.spbstu.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.spbstu.model.Question;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {
}