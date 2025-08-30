package ru.spbstu.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import ru.spbstu.model.User;
import ru.spbstu.model.UserRole;
import ru.spbstu.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

@Component
public class AdminInterceptor implements HandlerInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(AdminInterceptor.class);
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminInterceptor(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            logger.warn("Отсутствует или неверный заголовок Authorization: {}", authHeader);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader("WWW-Authenticate", "Basic realm=\"Admin Area\"");
            return false;
        }

        // Декодируем Base64
        String base64Credentials = authHeader.substring("Basic ".length());
        byte[] credDecoded = Base64.getDecoder().decode(base64Credentials);
        String credentials = new String(credDecoded, StandardCharsets.UTF_8);

        // credentials = "username:password"
        final String[] values = credentials.split(":", 2);
        if (values.length != 2) {
            logger.warn("Неверный формат учетных данных: {}", credentials);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        String login = values[0];
        String password = values[1];
        logger.debug("Попытка авторизации для пользователя: {}", login);

        Optional<User> userOpt = userRepository.findByLogin(login);
        if (userOpt.isEmpty() || userOpt.get().getRole() != UserRole.ADMIN
                || !passwordEncoder.matches(password, userOpt.get().getPasswordHash())) {
            logger.warn("Пользователь с ролью админ не найден: {}", login);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }

        logger.info("Успешная аутентификация администратора: {}", login);
        return true; // авторизован
    }
}
