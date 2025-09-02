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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.spbstu.api.DemoteToUserController;
import ru.spbstu.dto.UserDto;
import ru.spbstu.service.UserService;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.request.RequestDocumentation.*;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

@ExtendWith(RestDocumentationExtension.class)
public class DemoteToUserControllerTest extends AbstractControllerTest{

    private final UserService userService = Mockito.mock(UserService.class);

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDocumentation) {
        DemoteToUserController controller = new DemoteToUserController(userService);
        setupController(controller, restDocumentation);
    }

    @Test
    void demoteUser_success_generatesSnippets() throws Exception {
        UserDto dto = new UserDto(
                1L,
                12345L,
                "testuser",
                "testlogin",
                "passwordHash",
                LocalDateTime.now(),
                "USER",
                "Europe/Moscow",
                100
        );

        Mockito.when(userService.demoteUserFromAdmin(Mockito.eq(1L), Mockito.anyString()))
                .thenReturn(Optional.of(dto));

        // Мокаем атрибут adminLogin напрямую, чтобы interceptor не требовался
        RequestPostProcessor addAdminLogin = request -> {
            request.setAttribute("adminLogin", "admin");
            return request;
        };

        mockMvc.perform(post("/admin/users/{userId}/demote", 1)
                        .with(httpBasic("admin", "password"))
                        .with(addAdminLogin)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                )
                .andExpect(status().isOk())
                .andDo(document("demote-user",
                        requestHeaders(
                                headerWithName("Authorization").description("Basic Auth: username и password в base64")
                        ),
                        pathParameters(
                                parameterWithName("userId").description("ID пользователя, которого понижают")
                        ),
                        responseFields(
                                fieldWithPath("login").description("Логин пользователя"),
                                fieldWithPath("demoted").description("true, если понижение успешно")
                        )
                ));
    }
}
