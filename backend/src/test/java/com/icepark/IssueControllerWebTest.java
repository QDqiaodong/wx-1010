package com.icepark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icepark.config.GlobalExceptionHandler;
import com.icepark.controller.IssueController;
import com.icepark.dto.IssueRequestDTO;
import com.icepark.exception.ConflictException;
import com.icepark.service.IssueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP 层契约：并发落败必须是 409 且消息明确；参数问题是 400。
 * 不启动完整 Spring 容器，仅装配控制器 + 全局异常处理。
 */
class IssueControllerWebTest {

    private MockMvc mockMvc;
    private IssueService issueService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        issueService = mock(IssueService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new IssueController(issueService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void concurrentLoserGets409WithClearMessage() throws Exception {
        when(issueService.issue(eq(7L), any(IssueRequestDTO.class)))
                .thenThrow(new ConflictException("该器材已被其他游客领用，发装失败"));

        String body = """
            {
              "sessionEquipmentId": 100,
              "visitorId": "V1",
              "visitorName": "张三",
              "visitorAgeGroup": "CHILD",
              "measuredTemperature": -15.0,
              "operator": "staffA"
            }
            """;

        mockMvc.perform(post("/api/session/7/issue").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.error").value("该器材已被其他游客领用，发装失败"));
    }

    @Test
    void invalidPayloadGets400() throws Exception {
        // 缺少 operator / visitorId / measuredTemperature
        String body = """
            {
              "sessionEquipmentId": 100,
              "visitorAgeGroup": "CHILD"
            }
            """;

        mockMvc.perform(post("/api/session/7/issue").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }
}
