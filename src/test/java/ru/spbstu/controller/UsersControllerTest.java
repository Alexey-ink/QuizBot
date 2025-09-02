package ru.spbstu.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.restdocs.mockmvc.RestDocumentationResultHandler;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.spbstu.api.UsersController;
import ru.spbstu.dto.UserDto;
import ru.spbstu.service.UserService;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

@ExtendWith(RestDocumentationExtension.class)
public class UsersControllerTest extends AbstractControllerTest {

    private final UserService userService = Mockito.mock(UserService.class);

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDocumentation) {
        UsersController controller = new UsersController(userService);
        setupController(controller, restDocumentation);
    }

    @Test
    void getAllUsers_success_generatesSnippets() throws Exception {
        List<UserDto> users = List.of(
                new UserDto(1L, 12345L, "user1", null, null,
                        LocalDateTime.now(), "USER", "Europe/Moscow", 100),
                new UserDto(2L, 67890L, "user2", "login2",
                        "passwordHash", LocalDateTime.now(), "ADMIN", "Europe/Moscow", 200)
        );

        Mockito.when(userService.findAll()).thenReturn(users);

        mockMvc.perform(get("/admin/users")
                        .with(httpBasic("admin", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andDo(document("get-all-users",
                        requestHeaders(
                                headerWithName("Authorization").description("Basic Auth: username и password в base64")
                        ),
                        responseFields(
                                fieldWithPath("[].user_id").description("ID пользователя"),
                                fieldWithPath("[].telegram_id").description("Телеграм ID"),
                                fieldWithPath("[].username").description("Имя пользователя"),
                                fieldWithPath("[].login").description("Логин пользователя").optional(),
                                fieldWithPath("[].passwordHash").description("Хэш пароля").optional(),
                                fieldWithPath("[].role").description("Роль пользователя"),
                                fieldWithPath("[].time_zone").description("Часовой пояс пользователя"),
                                fieldWithPath("[].score").description("Баллы пользователя"),
                                fieldWithPath("[].created_at").description("Дата создания пользователя")
                        )
                ));
    }
}
