package ru.spbstu.api;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.spbstu.dto.UserDto;
import ru.spbstu.service.UserService;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/admin/users")
public class DemoteToUserController {

    private final UserService userService;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public DemoteToUserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/{userId}/demote")
    public ResponseEntity<?> demoteFromAdmin(@PathVariable("userId") Long userId,
                                             HttpServletRequest request) {

        String currentLogin = (String) request.getAttribute("adminLogin");
        logger.info("Received request to demote user with ID: {} by {}", userId, currentLogin);

        try {
            Optional<UserDto> userDto = userService.demoteUserFromAdmin(userId, currentLogin);

            if (userDto.isEmpty()) {
                logger.warn("User with ID: {} not found for demotion", userId);
                return ResponseEntity.notFound().build();
            }

            Map<String, Object> response = new HashMap<>();
            response.put("login", userDto.get().login());
            response.put("demoted", true);

            logger.info("User with ID: {} successfully demoted from admin", userId);
            return ResponseEntity.ok(response);

        } catch (IllegalStateException ex) {
            logger.warn("Попытка понизить роль у самого себя: {}", ex.getMessage());
            return ResponseEntity.status(403).body(Map.of("error", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            logger.warn("Bad request for demotion: {}. Юзер не является админом", ex.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            logger.error("Unexpected error while demoting user with ID {}: {}", userId, ex.getMessage(), ex);
            return ResponseEntity.status(500).body(Map.of("error", "internal error"));
        }
    }
}
