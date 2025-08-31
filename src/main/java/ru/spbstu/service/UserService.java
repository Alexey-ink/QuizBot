package ru.spbstu.service;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import ru.spbstu.dto.UserDto;
import ru.spbstu.model.User;
import ru.spbstu.model.UserRole;
import ru.spbstu.repository.UserRepository;

import java.util.List;
import java.util.Optional;
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

    public Optional<UserDto> promoteUserToAdmin(Long userId, String hashPassword) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            return Optional.empty();
        }

        if(userOptional.get().getRole().equals(UserRole.ADMIN)) {
            throw new IllegalArgumentException("User is already admin");
        }

        String login = userOptional.get().getUsername();
        if (login == null) {
            login = String.valueOf(userOptional.get().getTelegramId());
        }

        User user = userOptional.get();
        user.setLogin(login);
        user.setRole(UserRole.ADMIN);
        user.setPasswordHash(hashPassword);

        User savedUser = userRepository.save(user);
        return Optional.of(UserDto.toDto(savedUser));
    }

    public Optional<UserDto> demoteUserFromAdmin(Long userId, String currentLogin) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            return Optional.empty();
        }

        User target = userOptional.get();

        Optional<User> currentOpt = userRepository.findByLogin(currentLogin);
        if (currentOpt.isPresent() && currentOpt.get().getId().equals(userId)) {
            throw new IllegalStateException("Demotion of the currently authenticated admin is not allowed");
        }

        if (target.getRole() != UserRole.ADMIN) {
            throw new IllegalArgumentException("User is not an admin");
        }

        target.setRole(UserRole.USER);
        User savedUser = userRepository.save(target);
        return Optional.of(UserDto.toDto(savedUser));
    }
}
