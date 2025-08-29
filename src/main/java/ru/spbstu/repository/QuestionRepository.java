package ru.spbstu.repository;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    @Query("SELECT q FROM Question q WHERE q.user.id = :userId " +
            "AND SIZE(q.tags) = 1 " +
            "AND EXISTS (SELECT t FROM q.tags t WHERE t.id = :tagId)")
    List<Question> findQuestionsWithSingleTag(@Param("userId") Long userId,
                                              @Param("tagId") Long tagId);

    @Query("SELECT COUNT(q) > 0 FROM Question q JOIN q.tags t WHERE t.id = :tagId")
    boolean existsByTagId(@Param("tagId") Long tagId);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM question_tag qt " +
            "USING questions q " +
            "WHERE qt.question_id = q.id " +
            "AND qt.tag_id = :tagId " +
            "AND q.user_id = :userId",
            nativeQuery = true)
    void deleteTagFromQuestionsByTagIdAndUserId(@Param("tagId") Long tagId,
                                                @Param("userId") Long userId);

}