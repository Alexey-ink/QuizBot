package ru.spbstu.service;

import org.springframework.stereotype.Service;
import ru.spbstu.model.User;
import ru.spbstu.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User registerIfNotExists(Long telegramId, String username) {
        return userRepository.findByTelegramId(telegramId)
                .orElseGet(() -> userRepository.save(new User(telegramId, username)));
    }
}
