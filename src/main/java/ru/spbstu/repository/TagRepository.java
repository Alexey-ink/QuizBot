package ru.spbstu.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.spbstu.model.Tag;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {
    Optional<Tag> findByUserIdAndNameIgnoreCase(Long userId, String name);
    Optional<Tag> findByUserTelegramIdAndNameIgnoreCase(Long userId, String name);
    List<Tag> findAllByUserId(Long userId);
    @Query("select t from Tag t where lower(t.name) in :names")
    List<Tag> findAllByNameLowerIn(@Param("names") Collection<String> names);
}