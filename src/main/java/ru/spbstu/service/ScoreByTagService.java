package ru.spbstu.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.spbstu.model.ScoreByTag;
import ru.spbstu.model.Tag;
import ru.spbstu.model.User;
import ru.spbstu.repository.ScoreByTagRepository;
import ru.spbstu.repository.TagRepository;
import ru.spbstu.repository.UserQuestionRepository;
import ru.spbstu.repository.UserRepository;

@Service
public class ScoreByTagService {
    private final ScoreByTagRepository scoreByTagRepository;
    private final UserRepository userRepository;
    private final TagRepository tagRepository;
    private final UserQuestionRepository userQuestionRepository;

    public ScoreByTagService(ScoreByTagRepository scoreByTagRepository, UserRepository userRepository, TagRepository tagRepository, UserQuestionRepository userQuestionRepository) {
        this.scoreByTagRepository = scoreByTagRepository;
        this.userRepository = userRepository;
        this.tagRepository = tagRepository;
        this.userQuestionRepository = userQuestionRepository;
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

    public boolean tagExists(String tagName) {
        return tagRepository.findByNameIgnoreCase(tagName).isPresent();
    }

    @Transactional
    public void resetScore(Long telegramId) {
        Long userId = userRepository.findIdByTelegramId(telegramId);
        userRepository.resetScoreByUserId(userId);
        scoreByTagRepository.resetScoresByUserId(userId);
        userQuestionRepository.deleteAllByUserId(userId);
    }
}
