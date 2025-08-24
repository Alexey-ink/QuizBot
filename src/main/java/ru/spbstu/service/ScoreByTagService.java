package ru.spbstu.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.spbstu.model.ScoreByTag;
import ru.spbstu.model.Tag;
import ru.spbstu.model.User;
import ru.spbstu.repository.ScoreByTagRepository;
import ru.spbstu.repository.TagRepository;

@Service
public class ScoreByTagService {
    private final ScoreByTagRepository scoreByTagRepository;
    private final TagRepository tagRepository;

    public ScoreByTagService(ScoreByTagRepository scoreByTagRepository, TagRepository tagRepository) {
        this.scoreByTagRepository = scoreByTagRepository;
        this.tagRepository = tagRepository;
    }

    @Transactional
    public void incrementScore(User user, Tag tag) {
        ScoreByTag scoreByTag = scoreByTagRepository.findByUserAndTag(user, tag)
                .orElseGet(() -> new ScoreByTag(null, 0, user, tag));
        scoreByTag.setScore(scoreByTag.getScore() + 1);
        scoreByTagRepository.save(scoreByTag);
    }

    public Integer getScoreByUserIdAndTagName(Long telegramId, String tagName) {
        return scoreByTagRepository
                .findScoreByUserTelegramIdAndTagName(telegramId, tagName)
                .orElse(0);
    }

    public boolean tagExists(Long telegramId, String tagName) {
        return tagRepository.findByUserTelegramIdAndNameIgnoreCase(telegramId, tagName).isPresent();
    }

    public void resetScore(Long telegramId) {

    }
}
