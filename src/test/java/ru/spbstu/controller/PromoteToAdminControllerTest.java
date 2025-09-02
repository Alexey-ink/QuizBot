package ru.spbstu.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.spbstu.api.PromoteToAdminController;
import ru.spbstu.dto.UserDto;
import ru.spbstu.service.UserService;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.request.RequestDocumentation.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

@ExtendWith(RestDocumentationExtension.class)
public class PromoteToAdminControllerTest extends AbstractControllerTest {

    private final UserService userService = Mockito.mock(UserService.class);
    private final PasswordEncoder passwordEncoder = Mockito.mock(PasswordEncoder.class);

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDocumentation) {
        PromoteToAdminController controller = new PromoteToAdminController(userService, passwordEncoder);
        setupController(controller, restDocumentation);
    }

    @Test
    void promoteUser_success_generatesSnippets() throws Exception {
        UserDto dto = new UserDto(
                2L,
                54321L,
                "newuser",
                "newlogin",
                "passwordHash",
                LocalDateTime.now(),
                "USER",
                "Europe/Moscow",
                50
        );

        Mockito.when(userService.promoteUserToAdmin(Mockito.eq(2L), Mockito.anyString()))
                .thenReturn(Optional.of(dto));
        Mockito.when(passwordEncoder.encode(Mockito.anyString())).thenReturn("hashedPassword");

        RequestPostProcessor addAdminLogin = request -> {
            request.setAttribute("adminLogin", "admin");
            return request;
        };

        mockMvc.perform(post("/admin/users/{userId}/promote", 2)
                        .with(httpBasic("admin", "password"))
                        .with(addAdminLogin)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                )
                .andExpect(status().isOk())
                .andDo(document("promote-user",
                        requestHeaders(
                                headerWithName("Authorization").description("Basic Auth: username и password в base64")
                        ),
                        pathParameters(
                                parameterWithName("userId").description("ID пользователя, которого повышают")
                        ),
                        responseFields(
                                fieldWithPath("login").description("Логин пользователя"),
                                fieldWithPath("password").description("Сгенерированный пароль для нового администратора"),
                                fieldWithPath("promoted").description("true, если повышение успешно")
                        )
                ));
    }
}
