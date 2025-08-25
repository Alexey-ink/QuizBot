package ru.spbstu.service;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import ru.spbstu.dto.TagDto;
import ru.spbstu.model.Tag;
import ru.spbstu.model.User;
import ru.spbstu.repository.ScoreByTagRepository;
import ru.spbstu.repository.TagRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class TagService {

    private final TagRepository tagRepository;
    private final ScoreByTagRepository scoreByTagRepository;
    private final UserService userService;

    private static final int MAX_TAGS = 5;
    private static final int MAX_TAG_LENGTH = 30;
    private static final Pattern TAG_PATTERN = Pattern.compile("^[a-zа-я0-9_]+$");

    public TagService(TagRepository tagRepository, ScoreByTagRepository scoreByTagRepository, UserService userService) {
        this.tagRepository = tagRepository;
        this.scoreByTagRepository = scoreByTagRepository;
        this.userService = userService;
    }

    public List<String> parseAndValidateTags(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("Строка тегов пустая.");
        }

        List<String> tags = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.startsWith("#") ? s.substring(1) : s) // убрать #
                .map(String::toLowerCase) // нормализация
                .distinct()
                .collect(Collectors.toList());

        if (tags.isEmpty()) {
            throw new IllegalArgumentException("Нужно указать хотя бы один тег.");
        }
        if (tags.size() > MAX_TAGS) {
            throw new IllegalArgumentException("Максимум " + MAX_TAGS + " тегов.");
        }

        for (String t : tags) {
            if (t.length() > MAX_TAG_LENGTH) {
                throw new IllegalArgumentException("Тег '" + t + "' слишком длинный (макс " + MAX_TAG_LENGTH + " символов).");
            }
            if (!TAG_PATTERN.matcher(t).matches()) {
                throw new IllegalArgumentException("Тег '" + t + "' содержит недопустимые символы. Разрешены: a-z, а-я, 0-9 и '_'.");
            }
        }

        return tags;
    }

    @Transactional
    public List<TagDto> findAll() {
        return tagRepository.findAll().stream()
                .map(TagDto::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public Optional<TagDto> findByNameIgnoreCase(String tagName) {
        return tagRepository.findByNameIgnoreCase(tagName)
                .map(TagDto::toDto);
    }

    @Transactional
    public void deleteTagById(Long tagId) {
        tagRepository.deleteById(tagId);
    }

    @Transactional
    public void deleteScoreByTagId(Long tagId) {
        scoreByTagRepository.deleteByTagId(tagId);
    }

    @Transactional
    public void createNewTag(Long telegramId, String tagName) {
        User user = userService.getUser(telegramId);
        Tag tag = new Tag();
        tag.setName(tagName);
        tag.setUser(user);
        tagRepository.save(tag);
    }
}
