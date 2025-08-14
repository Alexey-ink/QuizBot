package ru.spbstu.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.spbstu.model.Question;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuestionRepository extends JpaRepository<Question, String> {
    
    @Query("SELECT q FROM Question q JOIN q.tags t JOIN t.user u WHERE u.id = :userId AND t.name = :tagName")
    List<Question> findByUserIdAndTagName(@Param("userId") Long userId, @Param("tagName") String tagName);
    
    @Query(value = "SELECT * FROM questions ORDER BY RANDOM() LIMIT 1", nativeQuery = true)
    Optional<Question> findRandomQuestion();
    
    @Query(value = "SELECT q.* FROM questions q " +
                   "JOIN question_tag qt ON q.id = qt.question_id " +
                   "JOIN tags t ON qt.tag_id = t.id " +
                   "WHERE t.name = :tagName " +
                   "ORDER BY RANDOM() LIMIT 1", nativeQuery = true)
    Optional<Question> findRandomQuestionByTag(@Param("tagName") String tagName);
}