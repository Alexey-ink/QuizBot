package ru.spbstu.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.spbstu.model.ScoreByTag;
import ru.spbstu.model.Tag;
import ru.spbstu.model.User;

import java.util.Optional;

public interface ScoreByTagRepository extends JpaRepository<ScoreByTag, String> {
    Optional<ScoreByTag> findByUserAndTag(User user, Tag tag);
}
