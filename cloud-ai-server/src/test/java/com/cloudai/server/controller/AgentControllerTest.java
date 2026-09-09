package com.cloudai.server.controller;

import com.cloudai.runtime.model.AgentResponse;
import com.cloudai.server.dto.AgentRunRequest;
import com.cloudai.server.service.AgentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;


import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("AgentController")
class AgentControllerTest {

    private MockMvc mockMvc;
    private AgentService agentService;
    
    @BeforeEach
    void setUp() {
        agentService = mock(AgentService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AgentController(agentService)).build();
            }

    @Nested
    @DisplayName("POST /api/agent/run")
    class RunEndpoint {
        @Test
        @DisplayName("should return 200 with response body on valid request")
        void shouldReturn200OnValidRequest() throws Exception {
            var response = new AgentResponse(
                    "trace-1", "hello world", List.of(),
                    1, 0, AgentResponse.FinishStatus.COMPLETED, "", null);
            when(agentService.run(eq("hi"), eq(null), eq(null), eq(null), eq(null)))
                    .thenReturn(response);

            mockMvc.perform(post("/api/agent/run")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"prompt\":\"hi\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.traceId").value("trace-1"))
                    .andExpect(jsonPath("$.content").value("hello world"))
                    .andExpect(jsonPath("$.finishStatus").value("COMPLETED"))
                    .andExpect(jsonPath("$.turnsExecuted").value(1));
        }

        @Test
        @DisplayName("should return 400 when prompt is blank")
        void shouldReturn400WhenPromptBlank() throws Exception {
            mockMvc.perform(post("/api/agent/run")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"prompt\":\"\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should accept all optional fields")
        void shouldAcceptAllOptionalFields() throws Exception {
            var response = new AgentResponse(
                    "my-trace", "done", List.of(),
                    2, 1, AgentResponse.FinishStatus.COMPLETED, "", null);
            when(agentService.run(eq("hello"), eq("openai"), eq(5),
                    eq(Duration.parse("PT1M")), eq("my-trace")))
                    .thenReturn(response);

            mockMvc.perform(post("/api/agent/run")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"prompt\":\"hello\",\"provider\":\"openai\",\"maxTurns\":5,\"timeout\":\"PT1M\",\"traceId\":\"my-trace\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.traceId").value("my-trace"));
        }
    }

    @Nested
    @DisplayName("GET /api/agent/status/{traceId}")
    class StatusEndpoint {
        @Test
        @DisplayName("should return running status")
        void shouldReturnRunningStatus() throws Exception {
            when(agentService.isRunning("trace-1")).thenReturn(true);

            mockMvc.perform(get("/api/agent/status/trace-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.traceId").value("trace-1"))
                    .andExpect(jsonPath("$.running").value(true));
        }

        @Test
        @DisplayName("should return not running status")
        void shouldReturnNotRunningStatus() throws Exception {
            when(agentService.isRunning("trace-2")).thenReturn(false);

            mockMvc.perform(get("/api/agent/status/trace-2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.running").value(false));
        }
    }

    @Nested
    @DisplayName("POST /api/agent/interrupt/{traceId}")
    class InterruptEndpoint {
        @Test
        @DisplayName("should return interrupted=true")
        void shouldReturnInterrupted() throws Exception {
            mockMvc.perform(post("/api/agent/interrupt/trace-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.traceId").value("trace-1"))
                    .andExpect(jsonPath("$.interrupted").value(true));
        }
    }
}

