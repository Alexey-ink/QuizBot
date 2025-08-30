package ru.spbstu.service;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import ru.spbstu.dto.UserDto;
import ru.spbstu.model.User;
import ru.spbstu.repository.UserRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getOrCreateUser(Long telegramId, String username) {
        return userRepository.findByTelegramId(telegramId)
                .orElseGet(() -> userRepository.save(new User(telegramId, username, 0)));
    }

    public User getUser(Long telegramId) {
        return userRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("User not found with telegramId: " + telegramId));
    }

    @Transactional
    public void updateUserTimezone(Long telegramId, String tz) {
        User user = userRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setTimeZone(tz);
        userRepository.save(user);
    }

    public void save(User user) {
        userRepository.save(user);
    }

    @Transactional
    public Long getUserIdByTelegramId(Long telegramId) {
        return userRepository.findIdByTelegramId(telegramId);
    }

    @Transactional
    public Integer getScoreIdByTelegramId(Long telegramId){
        return userRepository.findScoreByTelegramId(telegramId);
    }


    public List<UserDto> findAll() {
        return userRepository.findAll()
                .stream()
                .map(UserDto::toDto)
                .collect(Collectors.toList());
    }
}
