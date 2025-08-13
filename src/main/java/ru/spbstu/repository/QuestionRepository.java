package ru.spbstu.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.spbstu.model.Question;

import java.util.List;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {
    
    @Query("SELECT q FROM Question q JOIN q.tags t WHERE t.user.id = :userId AND t.name = :tagName")
    List<Question> findByUserIdAndTagName(@Param("userId") Long userId, @Param("tagName") String tagName);
}