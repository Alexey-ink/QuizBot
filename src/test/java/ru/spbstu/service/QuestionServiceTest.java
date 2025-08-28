package ru.spbstu.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.spbstu.dto.QuestionDto;
import ru.spbstu.model.*;
import ru.spbstu.repository.QuestionRepository;
import ru.spbstu.repository.TagRepository;
import ru.spbstu.repository.UserQuestionRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuestionServiceTest {

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private UserService userService;

    @Mock
    private UserQuestionRepository userQuestionRepository;

    @InjectMocks
    private QuestionService questionService;

    private User testUser;
    private Question testQuestion;
    private Tag testTag;

    @BeforeEach
    void setUp() {
        // Создаем тестового пользователя
        testUser = new User();
        testUser.setId(1L);
        testUser.setTelegramId(123456789L);
        testUser.setUsername("testuser");

        // Создаем тестовый тег
        testTag = new Tag();
        testTag.setId(1L);
        testTag.setName("java");
        testTag.setUser(testUser);

        // Создаем тестовый вопрос
        testQuestion = new Question();
        testQuestion.setId("123-001-456");
        testQuestion.setText("Что такое Java?");
        testQuestion.setCorrectOption(1);
        testQuestion.setUser(testUser);

        // Создаем варианты ответов
        Set<QuestionOption> options = Set.of(
            createQuestionOption(1, "Язык программирования", testQuestion),
            createQuestionOption(2, "Кофе", testQuestion),
            createQuestionOption(3, "Остров", testQuestion),
            createQuestionOption(4, "Автомобиль", testQuestion)
        );
        testQuestion.setOptions(options);
        testQuestion.setTags(Set.of(testTag));
    }

    private QuestionOption createQuestionOption(int number, String text, Question question) {
        QuestionOption option = new QuestionOption();
        option.setOptionNumber(number);
        option.setText(text);
        option.setQuestion(question);
        return option;
    }

    @Test
    void testSaveQuestion_Success() {
        // Arrange
        Long telegramId = 123456789L;
        String questionText = "Что такое Java?";
        List<String> answers = Arrays.asList("Язык программирования", "Кофе", "Остров", "Автомобиль");
        int correctOption = 1;
        List<String> tagNames = Arrays.asList("java", "programming");

        when(userService.getUser(telegramId)).thenReturn(testUser);
        when(tagRepository.findByNameIgnoreCase("java")).thenReturn(Optional.of(testTag));
        when(tagRepository.findByNameIgnoreCase("programming")).thenReturn(Optional.empty());
        when(tagRepository.save(any(Tag.class))).thenAnswer(invocation -> {
            Tag tag = invocation.getArgument(0);
            tag.setId(2L);
            return tag;
        });
        when(questionRepository.save(any(Question.class))).thenReturn(testQuestion);

        // Act
        String result = questionService.saveQuestion(telegramId, questionText, answers, correctOption, tagNames);

        // Assert
        assertNotNull(result);
        assertEquals(testQuestion.getId(), result);
        
        verify(userService).getUser(telegramId);
        verify(tagRepository, times(2)).findByNameIgnoreCase(anyString());
        verify(tagRepository).save(any(Tag.class));
        verify(questionRepository).save(any(Question.class));
    }

    @Test
    void testGetQuestionsByTag_Success() {
        // Arrange
        String tagName = "java";
        List<Question> questions = Arrays.asList(testQuestion);
        when(questionRepository.findByTagName(tagName)).thenReturn(questions);

        // Act
        List<QuestionDto> result = questionService.getQuestionsByTag(tagName);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testQuestion.getId(), result.get(0).id());
        assertEquals(testQuestion.getText(), result.get(0).text());
        assertEquals(testUser.getId(), result.get(0).userId());
        assertEquals(testUser.getTelegramId(), result.get(0).telegramId());

        verify(questionRepository).findByTagName(tagName);
    }

    @Test
    void testGetQuestionDtoById_Success() {
        // Arrange
        String questionId = "123-001-456";
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(testQuestion));

        // Act
        Optional<QuestionDto> result = questionService.getQuestionDtoById(questionId);

        // Assert
        assertTrue(result.isPresent());
        QuestionDto dto = result.get();
        assertEquals(testQuestion.getId(), dto.id());
        assertEquals(testQuestion.getText(), dto.text());
        assertEquals(testUser.getId(), dto.userId());
        assertEquals(testUser.getTelegramId(), dto.telegramId());

        verify(questionRepository).findById(questionId);
    }

    @Test
    void testGetQuestionDtoById_NotFound() {
        // Arrange
        String questionId = "non-existent";
        when(questionRepository.findById(questionId)).thenReturn(Optional.empty());

        // Act
        Optional<QuestionDto> result = questionService.getQuestionDtoById(questionId);

        // Assert
        assertFalse(result.isPresent());
        verify(questionRepository).findById(questionId);
    }

    @Test
    void testIsQuestionOwner_True() {
        // Arrange
        String questionId = "123-001-456";
        when(userService.getUser(testUser.getTelegramId())).thenReturn(testUser);
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(testQuestion));

        // Act
        boolean result = questionService.isQuestionOwner(testUser.getTelegramId(), questionId);

        // Assert
        assertTrue(result);
        verify(userService).getUser(testUser.getTelegramId());
        verify(questionRepository).findById(questionId);
    }

    @Test
    void testIsQuestionOwner_False() {
        // Arrange
        String questionId = "123-001-456";
        User otherUser = new User();
        otherUser.setId(2L);
        otherUser.setTelegramId(987654321L);

        Question otherQuestion = new Question();
        otherQuestion.setId(questionId);
        otherQuestion.setUser(otherUser);

        when(userService.getUser(testUser.getTelegramId())).thenReturn(testUser);
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(otherQuestion));

        // Act
        boolean result = questionService.isQuestionOwner(testUser.getTelegramId(), questionId);

        // Assert
        assertFalse(result);
        verify(userService).getUser(testUser.getTelegramId());
        verify(questionRepository).findById(questionId);
    }

    @Test
    void testGetSortedOptions_Success() {
        // Act
        List<String> result = questionService.getSortedOptions(testQuestion);

        // Assert
        assertNotNull(result);
        assertEquals(4, result.size());
        assertEquals("Язык программирования", result.get(0));
        assertEquals("Кофе", result.get(1));
        assertEquals("Остров", result.get(2));
        assertEquals("Автомобиль", result.get(3));
    }

    @Test
    void testTagExists_True() {
        // Arrange
        String tagName = "java";
        when(tagRepository.findByNameIgnoreCase(tagName)).thenReturn(Optional.of(testTag));

        // Act
        boolean result = questionService.tagExists(tagName);

        // Assert
        assertTrue(result);
        verify(tagRepository).findByNameIgnoreCase(tagName);
    }

    @Test
    void testTagExists_False() {
        // Arrange
        String tagName = "non-existent";
        when(tagRepository.findByNameIgnoreCase(tagName)).thenReturn(Optional.empty());

        // Act
        boolean result = questionService.tagExists(tagName);

        // Assert
        assertFalse(result);
        verify(tagRepository).findByNameIgnoreCase(tagName);
    }

    @Test
    void testDeleteQuestion_Success() {
        // Arrange
        String questionId = "123-001-456";

        // Act
        questionService.deleteQuestion(questionId);

        // Assert
        verify(questionRepository).deleteById(questionId);
    }

    @Test
    void testGetRandomQuestion_Success() {
        // Arrange
        Long userId = 1L;
        when(questionRepository.findRandomUnansweredQuestion(userId)).thenReturn(Optional.of(testQuestion));

        // Act
        Question result = questionService.getRandomQuestion(userId);

        // Assert
        assertNotNull(result);
        assertEquals(testQuestion.getId(), result.getId());
        verify(questionRepository).findRandomUnansweredQuestion(userId);
    }

    @Test
    void testGetRandomQuestion_NotFound() {
        // Arrange
        Long userId = 1L;
        when(questionRepository.findRandomUnansweredQuestion(userId)).thenReturn(Optional.empty());

        // Act
        Question result = questionService.getRandomQuestion(userId);

        // Assert
        assertNull(result);
        verify(questionRepository).findRandomUnansweredQuestion(userId);
    }

    @Test
    void testGetRandomQuestionByTag_Success() {
        // Arrange
        Long userId = 1L;
        String tagName = "java";
        when(questionRepository.findRandomUnansweredQuestionByTag(userId, tagName))
                .thenReturn(Optional.of(testQuestion));

        // Act
        Optional<Question> result = questionService.getRandomQuestionByTag(userId, tagName);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(testQuestion.getId(), result.get().getId());
        verify(questionRepository).findRandomUnansweredQuestionByTag(userId, tagName);
    }

    @Test
    void testGetRandomQuestionByTag_NotFound() {
        // Arrange
        Long userId = 1L;
        String tagName = "non-existent";
        when(questionRepository.findRandomUnansweredQuestionByTag(userId, tagName))
                .thenReturn(Optional.empty());

        // Act
        Optional<Question> result = questionService.getRandomQuestionByTag(userId, tagName);

        // Assert
        assertFalse(result.isPresent());
        verify(questionRepository).findRandomUnansweredQuestionByTag(userId, tagName);
    }

    @Test
    void testExistsAnsweredByTag_True() {
        // Arrange
        Long telegramId = 123456789L;
        String tagName = "java";
        when(userService.getUserIdByTelegramId(telegramId)).thenReturn(1L);
        when(userQuestionRepository.existsAnsweredByTag(1L, tagName)).thenReturn(true);

        // Act
        boolean result = questionService.existsAnsweredByTag(telegramId, tagName);

        // Assert
        assertTrue(result);
        verify(userService).getUserIdByTelegramId(telegramId);
        verify(userQuestionRepository).existsAnsweredByTag(1L, tagName);
    }

    @Test
    void testExistsAnsweredByTag_False() {
        // Arrange
        Long telegramId = 123456789L;
        String tagName = "java";
        when(userService.getUserIdByTelegramId(telegramId)).thenReturn(1L);
        when(userQuestionRepository.existsAnsweredByTag(1L, tagName)).thenReturn(false);

        // Act
        boolean result = questionService.existsAnsweredByTag(telegramId, tagName);

        // Assert
        assertFalse(result);
        verify(userService).getUserIdByTelegramId(telegramId);
        verify(userQuestionRepository).existsAnsweredByTag(1L, tagName);
    }
}
