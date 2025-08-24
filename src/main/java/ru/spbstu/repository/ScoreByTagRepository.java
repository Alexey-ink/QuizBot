package ru.spbstu.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.spbstu.model.ScoreByTag;
import ru.spbstu.model.Tag;
import ru.spbstu.model.User;

import java.util.Optional;

public interface ScoreByTagRepository extends JpaRepository<ScoreByTag, Long> {
    Optional<ScoreByTag> findByUserAndTag(User user, Tag tag);

    @Query("SELECT s.score FROM ScoreByTag s " +
            "WHERE s.user.telegramId = :userId AND LOWER(s.tag.name) = LOWER(:tagName)")
    Optional<Integer> findScoreByUserTelegramIdAndTagName(@Param("userId") Long userId,
                                                          @Param("tagName") String tagName);

    @Modifying
    @Query("UPDATE ScoreByTag s SET s.score = 0 WHERE s.user.id = :userId")
    void resetScoresByUserId(@Param("userId") Long userId);
}

