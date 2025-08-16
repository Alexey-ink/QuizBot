package ru.spbstu.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.spbstu.model.ScoreByTag;
import ru.spbstu.model.Tag;
import ru.spbstu.model.User;
import ru.spbstu.repository.ScoreByTagRepository;

@Service
public class ScoreByTagService {
    private final ScoreByTagRepository scoreByTagRepository;

    public ScoreByTagService(ScoreByTagRepository scoreByTagRepository) {
        this.scoreByTagRepository = scoreByTagRepository;
    }

    @Transactional
    public void incrementScore(User user, Tag tag) {
        ScoreByTag scoreByTag = scoreByTagRepository.findByUserAndTag(user, tag)
                .orElseGet(() -> new ScoreByTag(null, 0, user, tag));
        scoreByTag.setScore(scoreByTag.getScore() + 1);
        scoreByTagRepository.save(scoreByTag);
    }

    public int getScore(User user, Tag tag) {
        return scoreByTagRepository.findByUserAndTag(user, tag)
                .map(ScoreByTag::getScore)
                .orElse(0);
    }
}
