package ru.spbstu.controller;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.restdocs.mockmvc.RestDocumentationResultHandler;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.*;

@ExtendWith(RestDocumentationExtension.class)
public abstract class AbstractControllerTest {

    protected MockMvc mockMvc;
    protected RestDocumentationResultHandler restDocs;

    protected void setupController(Object controller, RestDocumentationContextProvider restDocumentation) {
        this.restDocs = document("{class-name}/{method-name}",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint())
        );

        this.mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .alwaysDo(restDocs)
                .apply(documentationConfiguration(restDocumentation))
                .build();
    }
}
