package ru.spbstu.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.spbstu.model.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByTelegramId(Long telegramId);
}
