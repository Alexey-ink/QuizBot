package ru.spbstu.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.spbstu.model.*;
import ru.spbstu.repository.QuestionRepository;
import ru.spbstu.repository.TagRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final TagRepository tagRepository;
    private final UserService userService;

    public QuestionService(QuestionRepository questionRepository, TagRepository tagRepository, UserService userService) {
        this.questionRepository = questionRepository;
        this.tagRepository = tagRepository;
        this.userService = userService;
    }

    @Transactional
    public String saveQuestion(Long telegramId, String text, List<String> answers, int correctOption, List<String> tagNames) {
        User user = userService.getUser(telegramId);

        Question question = new Question();
        question.setUser(user);
        question.setText(text);
        question.setCorrectOption(correctOption);

        Set<QuestionOption> options = new HashSet<>();
        for (int i = 0; i < answers.size(); i++) {
            QuestionOption option = new QuestionOption();
            option.setQuestion(question);
            option.setOptionNumber(i + 1);
            option.setText(answers.get(i));
            options.add(option);
        }
        question.setOptions(options);

        Set<Tag> tags = new HashSet<>();
        for (String tagName : tagNames) {
            Tag tag = tagRepository
                    .findByUserIdAndNameIgnoreCase(user.getId(), tagName)
                    .orElseGet(() -> {
                        Tag newTag = new Tag();
                        newTag.setUser(user);
                        newTag.setName(tagName);
                        return tagRepository.save(newTag);
                    });
            tags.add(tag);
        }
        question.setTags(tags);

        questionRepository.save(question);
        return question.getId();
    }

    public boolean tagExists(Long telegramId, String tagName) {
        User user = userService.getUser(telegramId);
        return tagRepository.findByUserIdAndNameIgnoreCase(user.getId(), tagName).isPresent();
    }

    public List<Question> getQuestionsByTag(Long telegramId, String tagName) {
        User user = userService.getUser(telegramId);
        return questionRepository.findByUserIdAndTagName(user.getId(), tagName);
    }

    public Question getQuestionById(Long questionId) {
        return questionRepository.findById(questionId).orElse(null);
    }

    public boolean isQuestionOwner(Long telegramId, Long questionId) {
        User user = userService.getUser(telegramId);
        Question question = getQuestionById(questionId);
        return question != null && question.getUser().getId().equals(user.getId());
    }

    @Transactional
    public void deleteQuestion(Long questionId) {
        questionRepository.deleteById(questionId);
    }
    
    @Transactional
    public void updateQuestion(Question question) {
        questionRepository.save(question);
    }
}
