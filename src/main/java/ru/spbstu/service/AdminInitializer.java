package ru.spbstu.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import ru.spbstu.model.User;
import ru.spbstu.model.UserRole;
import ru.spbstu.repository.UserRepository;

@Component
public class AdminInitializer {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.default.login}")
    private String defaultLogin;

    @Value("${admin.default.telegram-id}")
    private Long defaultTelegramId;

    @Value("${admin.default.password}")
    private String defaultPassword;

    public AdminInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    public void init() {
        userRepository.findByTelegramId(defaultTelegramId).ifPresentOrElse(
                u -> logger.info("Admin already exists: {}", defaultLogin),
                () -> {
                    User admin = new User();
                    admin.setLogin(defaultLogin);
                    admin.setTelegramId(defaultTelegramId);
                    admin.setPasswordHash(passwordEncoder.encode(defaultPassword));
                    admin.setRole(UserRole.ADMIN);
                    admin.setScore(0);
                    userRepository.save(admin);
                    logger.info("Default admin created: {}", defaultLogin);
                }
        );
    }
}