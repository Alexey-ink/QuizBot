package ru.spbstu.service;

import org.springframework.stereotype.Service;
import ru.spbstu.model.Tag;
import ru.spbstu.repository.TagRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class TagService {

    private final TagRepository tagRepository;

    private static final int MAX_TAGS = 5;
    private static final int MAX_TAG_LENGTH = 30;
    private static final Pattern TAG_PATTERN = Pattern.compile("^[a-zа-я0-9_]+$");

    public TagService(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
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

    public void ensureTagsDoNotExist(List<String> normalizedTags) {
        if (normalizedTags == null || normalizedTags.isEmpty()) return;

        Set<String> lowerNames = normalizedTags.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        List<Tag> existing = tagRepository.findAllByNameLowerIn(lowerNames);

        if (!existing.isEmpty()) {
            String found = existing.stream()
                    .map(Tag::getName)
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException("Теги уже существуют в базе: " + found);
        }
    }

    public List<Tag> findAllByUserId(Long userId) {
        return tagRepository.findAllByUserId(userId);
    }
}
