package ru.spbstu.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.spbstu.dto.TagDto;
import ru.spbstu.model.*;
import ru.spbstu.repository.ScoreByTagRepository;
import ru.spbstu.repository.TagRepository;
import ru.spbstu.repository.UserRepository;
import ru.spbstu.repository.UserQuestionRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceIntegrationTest {

    @Mock
    private TagRepository tagRepository;

    @Mock
    private ScoreByTagRepository scoreByTagRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserQuestionRepository userQuestionRepository;

    @InjectMocks
    private TagService tagService;

    @InjectMocks
    private UserService userService;

    @InjectMocks
    private ScoreByTagService scoreByTagService;

    private User testUser;
    private Tag testTag;
    private ScoreByTag testScoreByTag;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setTelegramId(123456789L);
        testUser.setUsername("testuser");
        testUser.setScore(0);

        testTag = new Tag();
        testTag.setId(1L);
        testTag.setName("java");
        testTag.setUser(testUser);

        testScoreByTag = new ScoreByTag();
        testScoreByTag.setId(1L);
        testScoreByTag.setScore(5);
        testScoreByTag.setUser(testUser);
        testScoreByTag.setTag(testTag);
    }

    // ========== TagService Tests ==========

    @Test
    void testParseAndValidateTags_Success() {
        // Arrange
        String rawTags = "java, programming, spring";

        // Act
        List<String> result = tagService.parseAndValidateTags(rawTags);

        // Assert
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals("java", result.get(0));
        assertEquals("programming", result.get(1));
        assertEquals("spring", result.get(2));
    }

    @Test
    void testParseAndValidateTags_WithHashSymbols() {
        // Arrange
        String rawTags = "#java, #programming";

        // Act
        List<String> result = tagService.parseAndValidateTags(rawTags);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("java", result.get(0));
        assertEquals("programming", result.get(1));
    }

    @Test
    void testParseAndValidateTags_EmptyString() {
        // Arrange
        String rawTags = "";

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            tagService.parseAndValidateTags(rawTags);
        });
    }

    @Test
    void testParseAndValidateTags_TooManyTags() {
        // Arrange
        String rawTags = "tag1, tag2, tag3, tag4, tag5, tag6";

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            tagService.parseAndValidateTags(rawTags);
        });
    }

    @Test
    void testParseAndValidateTags_TagTooLong() {
        // Arrange
        String longTag = "a".repeat(31); // 31 символов
        String rawTags = "java, " + longTag;

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            tagService.parseAndValidateTags(rawTags);
        });
    }

    @Test
    void testParseAndValidateTags_InvalidCharacters() {
        // Arrange
        String rawTags = "java, programming!";

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            tagService.parseAndValidateTags(rawTags);
        });
    }

    @Test
    void testFindByNameIgnoreCase_Success() {
        // Arrange
        String tagName = "java";
        when(tagRepository.findByNameIgnoreCase(tagName)).thenReturn(Optional.of(testTag));

        // Act
        Optional<TagDto> result = tagService.findByNameIgnoreCase(tagName);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(testTag.getId(), result.get().id());
        assertEquals(testTag.getName(), result.get().name());
        verify(tagRepository).findByNameIgnoreCase(tagName);
    }

    @Test
    void testFindByNameIgnoreCase_NotFound() {
        // Arrange
        String tagName = "non-existent";
        when(tagRepository.findByNameIgnoreCase(tagName)).thenReturn(Optional.empty());

        // Act
        Optional<TagDto> result = tagService.findByNameIgnoreCase(tagName);

        // Assert
        assertFalse(result.isPresent());
        verify(tagRepository).findByNameIgnoreCase(tagName);
    }

    @Test
    void testFindAll_Success() {
        // Arrange
        Tag tag1 = new Tag();
        tag1.setId(1L);
        tag1.setName("java");
        tag1.setUser(testUser);

        Tag tag2 = new Tag();
        tag2.setId(2L);
        tag2.setName("spring");
        tag2.setUser(testUser);

        List<Tag> tags = Arrays.asList(tag1, tag2);
        when(tagRepository.findAll()).thenReturn(tags);

        // Act
        List<TagDto> result = tagService.findAll();

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("java", result.get(0).name());
        assertEquals("spring", result.get(1).name());
        verify(tagRepository).findAll();
    }

    @Test
    void testDeleteTagById_Success() {
        // Arrange
        Long tagId = 1L;

        // Act
        tagService.deleteTagById(tagId);

        // Assert
        verify(tagRepository).deleteById(tagId);
    }

    @Test
    void testDeleteScoreByTagId_Success() {
        // Arrange
        Long tagId = 1L;

        // Act
        tagService.deleteScoreByTagId(tagId);

        // Assert
        verify(scoreByTagRepository).deleteByTagId(tagId);
    }

    // ========== UserService Tests ==========

    @Test
    void testGetOrCreateUser_ExistingUser() {
        // Arrange
        Long telegramId = 123456789L;
        String username = "testuser";
        when(userRepository.findByTelegramId(telegramId)).thenReturn(Optional.of(testUser));

        // Act
        User result = userService.getOrCreateUser(telegramId, username);

        // Assert
        assertNotNull(result);
        assertEquals(testUser.getId(), result.getId());
        assertEquals(testUser.getTelegramId(), result.getTelegramId());
        verify(userRepository).findByTelegramId(telegramId);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testGetOrCreateUser_NewUser() {
        // Arrange
        Long telegramId = 987654321L;
        String username = "newuser";
        when(userRepository.findByTelegramId(telegramId)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act
        User result = userService.getOrCreateUser(telegramId, username);

        // Assert
        assertNotNull(result);
        verify(userRepository).findByTelegramId(telegramId);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void testGetUser_Success() {
        // Arrange
        Long telegramId = 123456789L;
        when(userRepository.findByTelegramId(telegramId)).thenReturn(Optional.of(testUser));

        // Act
        User result = userService.getUser(telegramId);

        // Assert
        assertNotNull(result);
        assertEquals(testUser.getId(), result.getId());
        verify(userRepository).findByTelegramId(telegramId);
    }

    @Test
    void testGetUser_NotFound() {
        // Arrange
        Long telegramId = 999999999L;
        when(userRepository.findByTelegramId(telegramId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            userService.getUser(telegramId);
        });
        verify(userRepository).findByTelegramId(telegramId);
    }

    @Test
    void testUpdateUserTimezone_Success() {
        // Arrange
        Long telegramId = 123456789L;
        String timezone = "Europe/Moscow";
        when(userRepository.findByTelegramId(telegramId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act
        userService.updateUserTimezone(telegramId, timezone);

        // Assert
        verify(userRepository).findByTelegramId(telegramId);
        verify(userRepository).save(testUser);
        assertEquals(timezone, testUser.getTimeZone());
    }

    @Test
    void testUpdateUserTimezone_UserNotFound() {
        // Arrange
        Long telegramId = 999999999L;
        String timezone = "Europe/Moscow";
        when(userRepository.findByTelegramId(telegramId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            userService.updateUserTimezone(telegramId, timezone);
        });
        verify(userRepository).findByTelegramId(telegramId);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testGetUserIdByTelegramId_Success() {
        // Arrange
        Long telegramId = 123456789L;
        Long userId = 1L;
        when(userRepository.findIdByTelegramId(telegramId)).thenReturn(userId);

        // Act
        Long result = userService.getUserIdByTelegramId(telegramId);

        // Assert
        assertEquals(userId, result);
        verify(userRepository).findIdByTelegramId(telegramId);
    }

    @Test
    void testGetScoreIdByTelegramId_Success() {
        // Arrange
        Long telegramId = 123456789L;
        Integer score = 10;
        when(userRepository.findScoreByTelegramId(telegramId)).thenReturn(score);

        // Act
        Integer result = userService.getScoreIdByTelegramId(telegramId);

        // Assert
        assertEquals(score, result);
        verify(userRepository).findScoreByTelegramId(telegramId);
    }

    // ========== ScoreByTagService Tests ==========

    @Test
    void testIncrementScore_ExistingScore() {
        // Arrange
        when(scoreByTagRepository.findByUserAndTag(testUser, testTag))
                .thenReturn(Optional.of(testScoreByTag));
        when(scoreByTagRepository.save(any(ScoreByTag.class))).thenReturn(testScoreByTag);

        // Act
        scoreByTagService.incrementScore(testUser, testTag);

        // Assert
        assertEquals(6, testScoreByTag.getScore()); // было 5, стало 6
        verify(scoreByTagRepository).findByUserAndTag(testUser, testTag);
        verify(scoreByTagRepository).save(testScoreByTag);
    }

    @Test
    void testIncrementScore_NewScore() {
        // Arrange
        when(scoreByTagRepository.findByUserAndTag(testUser, testTag))
                .thenReturn(Optional.empty());
        when(scoreByTagRepository.save(any(ScoreByTag.class))).thenReturn(testScoreByTag);

        // Act
        scoreByTagService.incrementScore(testUser, testTag);

        // Assert
        verify(scoreByTagRepository).findByUserAndTag(testUser, testTag);
        verify(scoreByTagRepository).save(any(ScoreByTag.class));
    }

    @Test
    void testGetScoreByUserIdAndTagName_Success() {
        // Arrange
        Long telegramId = 123456789L;
        String tagName = "java";
        Integer score = 5;
        when(scoreByTagRepository.findScoreByUserTelegramIdAndTagName(telegramId, tagName))
                .thenReturn(Optional.of(score));

        // Act
        Integer result = scoreByTagService.getScoreByUserIdAndTagName(telegramId, tagName);

        // Assert
        assertEquals(score, result);
        verify(scoreByTagRepository).findScoreByUserTelegramIdAndTagName(telegramId, tagName);
    }

    @Test
    void testGetScoreByUserIdAndTagName_NotFound() {
        // Arrange
        Long telegramId = 123456789L;
        String tagName = "non-existent";
        when(scoreByTagRepository.findScoreByUserTelegramIdAndTagName(telegramId, tagName))
                .thenReturn(Optional.empty());

        // Act
        Integer result = scoreByTagService.getScoreByUserIdAndTagName(telegramId, tagName);

        // Assert
        assertEquals(0, result); // Должен вернуть 0 по умолчанию
        verify(scoreByTagRepository).findScoreByUserTelegramIdAndTagName(telegramId, tagName);
    }

    @Test
    void testTagExists_True() {
        // Arrange
        String tagName = "java";
        when(tagRepository.findByNameIgnoreCase(tagName)).thenReturn(Optional.of(testTag));

        // Act
        boolean result = scoreByTagService.tagExists(tagName);

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
        boolean result = scoreByTagService.tagExists(tagName);

        // Assert
        assertFalse(result);
        verify(tagRepository).findByNameIgnoreCase(tagName);
    }

    @Test
    void testResetScore_Success() {
        // Arrange
        Long telegramId = 123456789L;
        Long userId = 1L;
        when(userRepository.findIdByTelegramId(telegramId)).thenReturn(userId);

        // Act
        scoreByTagService.resetScore(telegramId);

        // Assert
        verify(userRepository).findIdByTelegramId(telegramId);
        verify(userRepository).resetScoreByUserId(userId);
        verify(scoreByTagRepository).resetScoresByUserId(userId);
        verify(userQuestionRepository).deleteAllByUserId(userId);
    }
}
