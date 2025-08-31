package ru.spbstu.api;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.spbstu.service.QuestionService;
import ru.spbstu.service.UserService;
import ru.spbstu.dto.UserDto;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/admin/questions")
public class QuestionUploadController {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final QuestionService questionService;
    private final UserService userService;

    public QuestionUploadController(QuestionService questionService, UserService userService) {
        this.questionService = questionService;
        this.userService = userService;
    }

    @PostMapping("/upload-csv")
    public ResponseEntity<?> uploadCsv(@RequestParam("file") MultipartFile file,
                                       HttpServletRequest request) {

        String adminLogin = (String) request.getAttribute("adminLogin");
        logger.info("Admin '{}' requested to upload questions from CSV file: {}",
                adminLogin, file.getOriginalFilename());

        Optional<UserDto> userDto = userService.findByLogin(adminLogin);

        if (userDto.isEmpty()) throw new RuntimeException("Admin user not found with login: " + adminLogin);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            int lineNumber = 0;
            int successCount = 0;
            List<String> errors = new ArrayList<>();
            String line;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                if (line.trim().isEmpty() || lineNumber == 1 && mightBeHeader(line)) {
                    continue;
                }

                try {
                    CsvQuestionData dataDto = parseCsvLine(line, lineNumber);

                    questionService.saveQuestion(
                            userDto.get().telegram_id(),
                            dataDto.getQuestion(),
                            dataDto.getAnswers(),
                            dataDto.getCorrectOption(),
                            dataDto.getTags()
                    );

                    successCount++;
                    logger.debug("Successfully processed line {}: {}", lineNumber, dataDto.getQuestion());

                } catch (Exception e) {
                    String errorMsg = String.format("Line %d: %s", lineNumber, e.getMessage());
                    errors.add(errorMsg);
                    logger.warn("Error processing line {}: {}", lineNumber, e.getMessage());
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("processed", lineNumber);
            response.put("successful", successCount);
            response.put("errors", errors.size());

            if (errors.isEmpty()) {
                response.put("message", "All questions processed successfully");
                logger.info("CSV upload completed successfully. Processed: {}, Successful: {}", lineNumber, successCount);
                return ResponseEntity.ok(response);
            } else {
                response.put("errorDetails", errors);
                response.put("message", "Some questions failed to process");
                logger.warn("CSV upload completed with errors. Processed: {}, Successful: {}, Errors: {}",
                        lineNumber, successCount, errors.size());
                return ResponseEntity.unprocessableEntity().body(response);
            }

        } catch (IOException e) {
            logger.error("Failed to read CSV file: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to read file",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            logger.error("Unexpected error during CSV processing: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Unexpected error",
                    "message", e.getMessage()
            ));
        }
    }

    private boolean mightBeHeader(String line) {
        return line.toLowerCase().contains("question") ||
                line.toLowerCase().contains("answer") ||
                line.toLowerCase().contains("tag");
    }

    private CsvQuestionData parseCsvLine(String line, int lineNumber) {
        String[] parts = parseCsv(line);

        if (parts.length < 7) {
            throw new IllegalArgumentException("Not enough fields. Expected at least 7 (question, 4 answers, correct option, 1+ tags)");
        }

        String question = parts[0].trim();
        if (question.isEmpty()) {
            throw new IllegalArgumentException("Question text is empty");
        }

        List<String> answers = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            String answer = parts[i].trim();
            if (answer.isEmpty()) {
                throw new IllegalArgumentException("Answer " + i + " is empty");
            }
            answers.add(answer);
        }

        int correctOption;
        try {
            correctOption = Integer.parseInt(parts[5].trim());
            if (correctOption < 1 || correctOption > 4) {
                throw new IllegalArgumentException("Correct option must be between 1 and 4");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Correct option must be a number between 1 and 4");
        }

        List<String> tags = new ArrayList<>();
        for (int i = 6; i < parts.length && i < 11; i++) {
            String tag = parts[i].trim();
            if (!tag.isEmpty()) {
                tag = tag.replaceAll("\\s+", "_")
                        .toLowerCase();
                tags.add(tag);
            }
        }

        if (tags.isEmpty()) {
            throw new IllegalArgumentException("At least one tag is required");
        }

        return new CsvQuestionData(question, answers, correctOption, tags);
    }

    private String[] parseCsv(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;

        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(currentField.toString());
                currentField.setLength(0);
            } else {
                currentField.append(c);
            }
        }
        fields.add(currentField.toString());

        return fields.toArray(new String[0]);
    }

    private static class CsvQuestionData {
        private final String question;
        private final List<String> answers;
        private final int correctOption;
        private final List<String> tags;

        public CsvQuestionData(String question, List<String> answers, int correctOption, List<String> tags) {
            this.question = question;
            this.answers = answers;
            this.correctOption = correctOption;
            this.tags = tags;
        }

        public String getQuestion() { return question; }
        public List<String> getAnswers() { return answers; }
        public int getCorrectOption() { return correctOption; }
        public List<String> getTags() { return tags; }
    }
}