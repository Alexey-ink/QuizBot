package ru.spbstu.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.spbstu.model.Tag;

import java.util.List;
import java.util.Optional;

@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {
    Optional<Tag> findByUserIdAndNameIgnoreCase(Long userId, String name);
    List<Tag> findAllByUserId(Long userId);
}