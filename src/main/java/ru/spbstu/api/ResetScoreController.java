package ru.spbstu.api;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.spbstu.service.ScoreByTagService;
import ru.spbstu.service.UserService;

import java.util.Map;

@RestController
@RequestMapping("/admin/users")
public class ResetScoreController {

    private final ScoreByTagService scoreByTagService;
    private final UserService userService;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public ResetScoreController(ScoreByTagService scoreByTagService, UserService userService) {
        this.scoreByTagService = scoreByTagService;
        this.userService = userService;
    }

    @PostMapping("/{userId}/reset-score")
    public ResponseEntity<?> resetScore(@PathVariable("userId") Long userId,
                                        HttpServletRequest request) {

        String adminLogin = (String) request.getAttribute("adminLogin");
        logger.info("Admin '{}' requested to reset score for user ID: {}", adminLogin, userId);

        if (!userService.userExists(userId)) {
            logger.warn("Admin '{}' attempted to reset score for non-existent user ID: {}", adminLogin, userId);
            return ResponseEntity.notFound().build();
        }

        try {
            scoreByTagService.resetScoreByUserId(userId);

            Map<String, Object> response = Map.of(
                    "success", true,
                    "message", "Score successfully reset",
                    "userId", userId,
                    "resetBy", adminLogin
            );

            logger.info("Admin '{}' successfully reset score for user (ID: {})",
                    adminLogin, userId);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Admin '{}' failed to reset score for user ID: {}. Error: {}",
                    adminLogin, userId, e.getMessage(), e);

            Map<String, Object> errorResponse = Map.of(
                    "success", false,
                    "message", "Failed to reset score",
                    "userId", userId,
                    "error", e.getMessage()
            );

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}