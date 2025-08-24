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
    
    @Query("SELECT q FROM Question q JOIN q.tags t JOIN t.user u WHERE t.name = :tagName")
    List<Question> findByTagName(@Param("tagName") String tagName);

    @Query(value = "SELECT q.* FROM questions q " +
            "LEFT JOIN user_question uq ON q.id = uq.question_id AND uq.user_id = :userId " +
            "WHERE uq.id IS NULL " +
            "ORDER BY RANDOM() LIMIT 1",
            nativeQuery = true)
    Optional<Question> findRandomUnansweredQuestion(@Param("userId") Long userId);


    @Query(value = "SELECT q.* FROM questions q " +
                   "LEFT JOIN question_tag qt ON q.id = qt.question_id " +
                   "JOIN tags t ON qt.tag_id = t.id " +
                   "LEFT JOIN user_question uq ON q.id = uq.question_id AND uq.user_id = :userId " +
                   "WHERE t.name = :tagName AND uq.id IS NULL " +
                   "ORDER BY RANDOM() LIMIT 1", nativeQuery = true)
    Optional<Question> findRandomUnansweredQuestionByTag(@Param("userId") Long userId,
                                                         @Param("tagName") String tagName);
}