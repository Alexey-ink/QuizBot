package ru.spbstu.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/")
public class HealthСheckController {

    @GetMapping("/healthcheck")
    public ResponseEntity<Map<String, Object>> healthcheck() {
        String status = "Application is running successfully";

        List<Map<String, String>> authors = List.of(
                Map.of("fullName", "Шихалев Алексей", "group", "5130201/20102"),
                Map.of("fullName", "Емешкин Максим", "group", "5130201/20101")
        );

        Map<String, Object> response = Map.of(
                "status", status,
                "authors", authors
        );

        return ResponseEntity.ok(response);
    }
}
