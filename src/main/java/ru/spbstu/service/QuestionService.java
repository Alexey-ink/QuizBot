package ru.spbstu.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.spbstu.dto.QuestionDto;
import ru.spbstu.model.*;
import ru.spbstu.repository.QuestionRepository;
import ru.spbstu.repository.TagRepository;
import ru.spbstu.repository.UserQuestionRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final TagRepository tagRepository;
    private final UserService userService;
    private final UserQuestionRepository userQuestionRepository;

    public QuestionService(QuestionRepository questionRepository,
                           TagRepository tagRepository,
                           UserService userService,
                           UserQuestionRepository userQuestionRepository) {
        this.questionRepository = questionRepository;
        this.tagRepository = tagRepository;
        this.userService = userService;
        this.userQuestionRepository = userQuestionRepository;
    }

    @Transactional
    public String saveQuestion(Long telegramId, String text, List<String> answers,
                               int correctOption, List<String> tagNames) {
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
                    .findByNameIgnoreCase(tagName)
                    .orElseGet(() -> {
                        Tag newTag = new Tag();
                        newTag.setUser(user);
                        newTag.setName(tagName);
                        return tagRepository.save(newTag);
                    });
            tags.add(tag);
        }
        question.setTags(tags);

        Question savedQuestion = questionRepository.save(question);
        return savedQuestion.getId();
    }

    public boolean tagExists(String tagName) {
        return tagRepository.findByNameIgnoreCase(tagName).isPresent();
    }

    @Transactional
    public List<QuestionDto> getQuestionsByTag(String tagName) {
        return questionRepository.findByTagName(tagName)
                .stream().map(QuestionDto::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public Optional<QuestionDto> getQuestionDtoById(String questionId) {
        return questionRepository.findById(questionId)
                .map(QuestionDto::toDto);
    }

    private Optional<Question> getQuestionById(String questionId) {
        return questionRepository.findById(questionId);
    }

    public boolean isQuestionOwner(Long telegramId, String questionId) {
        User user = userService.getUser(telegramId);
        Optional<Question> question = getQuestionById(questionId);
        return question.isPresent() &&
                question.get().getUser().getId().equals(user.getId());
    }

    @Transactional
    public void deleteQuestion(String questionId) {
        questionRepository.deleteById(questionId);
    }

    @Transactional(readOnly = true)
    public Question getRandomQuestion(Long userId) {
        return questionRepository.findRandomUnansweredQuestion(userId).orElse(null);
    }

    @Transactional(readOnly = true)
    public Optional<Question> getRandomQuestionByTag(Long userId, String tagName) {
        return questionRepository.findRandomUnansweredQuestionByTag(userId, tagName);
    }

    @Transactional
    public void updateQuestion(Question question) {
        questionRepository.save(question);
    }

    public List<String> getSortedOptions(Question randomQuestion) {
        List<QuestionOption> sortedOptions = randomQuestion.getOptions().stream()
                .sorted(Comparator.comparingInt(QuestionOption::getOptionNumber))
                .toList();

        return sortedOptions.stream()
                .map(QuestionOption::getText)
                .toList();
    }

    public boolean existsAnsweredByTag(Long telegramId, String tagName) {
        Long userId = userService.getUserIdByTelegramId(telegramId);
        return userQuestionRepository.existsAnsweredByTag(userId, tagName);
    }

    @Transactional
    public void deleteQuestionsWithSingleTag(Long userId, Long tagId) {
        List<Question> questions = questionRepository.findQuestionsWithSingleTag(userId, tagId);
        List<String> questionIds = questions.stream()
                .map(Question::getId)
                .collect(Collectors.toList());

        userQuestionRepository.deleteAllByQuestionIdIn(questionIds);
        questionRepository.deleteAll(questions);
    }

    @Transactional
    public boolean existsQuestionsByTagId(Long tagId) {
        return questionRepository.existsByTagId(tagId);
    }

    public void deleteTagFromQuestionsByTagId(Long userId, Long tagId) {
        questionRepository.deleteTagFromQuestionsByTagIdAndUserId(tagId, userId);
    }
}