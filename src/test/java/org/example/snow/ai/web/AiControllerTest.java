package org.example.snow.ai.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.snow.ai.application.GeneratedAnswer;
import org.example.snow.ai.application.OllamaService;
import org.example.snow.global.config.JacksonConfig;
import org.example.snow.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiControllerTest {

    private MockMvc mockMvc;

    private final OllamaService ollamaService = mock(OllamaService.class);
    private final ObjectMapper objectMapper = new JacksonConfig().objectMapper();

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new AiController(ollamaService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void returnsStructuredGeneratedAnswer() throws Exception {
        when(ollamaService.generateGroundedAnswer(any()))
                .thenReturn(new GeneratedAnswer(
                        "RAG는 검색 결과를 근거로 답변하는 방식입니다.",
                        java.util.List.of("sec-1", "sec-2"),
                        true
                ));

        mockMvc.perform(post("/api/ai/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "RAG가 뭐야?",
                                  "sections": [
                                    {
                                      "sectionId": "sec-1",
                                      "heading": "RAG Overview",
                                      "text": "RAG는 검색 증강 생성이다.",
                                      "documentName": "lecture.pdf",
                                      "sourceStartIndex": 1,
                                      "sourceEndIndex": 1,
                                      "rank": 1,
                                      "similarityScore": 0.98
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("RAG는 검색 결과를 근거로 답변하는 방식입니다."))
                .andExpect(jsonPath("$.citedSectionIds[0]").value("sec-1"))
                .andExpect(jsonPath("$.answerable").value(true));
    }

    @Test
    void rejectsBlankQuestion() throws Exception {
        mockMvc.perform(post("/api/ai/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "   ",
                                  "sections": [
                                    {
                                      "sectionId": "sec-1",
                                      "text": "RAG는 검색 증강 생성이다."
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"))
                .andExpect(jsonPath("$.message").value("질문은 필수입니다."));
    }
}
