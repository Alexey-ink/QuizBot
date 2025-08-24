package ru.spbstu.repository;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.spbstu.model.UserQuestion;
import java.util.Optional;

public interface UserQuestionRepository extends JpaRepository<UserQuestion, Long> {
    Optional<UserQuestion> findByUserIdAndQuestionId(Long userId, String questionId);

    @Query(value = "SELECT EXISTS (" +
            "SELECT 1 FROM user_question uq " +
            "JOIN questions q ON uq.question_id = q.id " +
            "JOIN question_tag qt ON q.id = qt.question_id " +
            "JOIN tags t ON qt.tag_id = t.id " +
            "WHERE uq.user_id = :userId AND t.name = :tagName" +
            ")", nativeQuery = true)
    boolean existsAnsweredByTag(@Param("userId") Long userId,
                                @Param("tagName") String tagName);

    @Modifying
    @Transactional
    @Query("DELETE FROM UserQuestion uq WHERE uq.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}