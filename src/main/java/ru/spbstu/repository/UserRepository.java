package ru.spbstu.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.spbstu.model.User;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByTelegramId(Long telegramId);

    @Query("SELECT u.id FROM User u WHERE u.telegramId = :telegramId")
    Long findIdByTelegramId(@Param("telegramId") Long telegramId);

    @Modifying
    @Query("UPDATE User u SET u.score = 0 WHERE u.id = :userId")
    void resetScoreByUserId(@Param("userId") Long userId);


}
