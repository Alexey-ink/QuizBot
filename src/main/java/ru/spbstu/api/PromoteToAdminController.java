package ru.spbstu.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import ru.spbstu.dto.UserDto;
import ru.spbstu.service.UserService;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/admin/users")
public class PromoteToAdminController {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public PromoteToAdminController(UserService userService, PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/{userId}/promote")
    public ResponseEntity<?> promoteToAdmin(@PathVariable("userId") Long userId) {
        logger.info("Received request to promote user with ID: {} to admin", userId);

        String password = generatePassword(8);
        String hashPassword = passwordEncoder.encode(password);
        try {
            Optional<UserDto> userDto = userService.promoteUserToAdmin(userId, hashPassword);

            if (userDto.isEmpty()) {
                logger.warn("User with ID: {} not found for promotion", userId);
                return ResponseEntity.notFound().build();
            }

            Map<String, Object> response = new HashMap<>();
            response.put("login", userDto.get().login());
            response.put("password", password);
            response.put("promoted", true);

            logger.info("User with ID: {} successfully promoted to admin. New login: {}",
                    userId, userDto.get().login());

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException ex) {
            logger.warn("User with id {} is already admin", userId);
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error while promoting user {}", userId);
            return ResponseEntity.status(500).body(Map.of("error", "internal error"));
        }
    }

    private static String generatePassword(int length) {
        SecureRandom rnd = new SecureRandom();
        byte[] bytes = new byte[length];
        rnd.nextBytes(bytes);
        // Base64 URL без padding, обрезаем до нужной длины
        String s = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return s.length() > length ? s.substring(0, length) : s;
    }

}