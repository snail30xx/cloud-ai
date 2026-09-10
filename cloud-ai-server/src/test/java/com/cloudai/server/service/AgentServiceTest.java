package com.cloudai.server.service;

import com.cloudai.execution.registry.ToolRegistry;
import com.cloudai.memory.MemoryEntry;
import com.cloudai.memory.MemoryType;
import com.cloudai.memory.MemoryRetriever;
import com.cloudai.memory.MemoryStore;
import com.cloudai.persona.Persona;
import com.cloudai.persona.PersonaAssembler;
import com.cloudai.persona.PersonaProvider;
import com.cloudai.runtime.AgentRequest;
import com.cloudai.runtime.AgentResponse;
import com.cloudai.runtime.AgentLoop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("AgentService")
class AgentServiceTest {

    private AgentLoop agentLoop;
    private PersonaProvider personaProvider;
    private PersonaAssembler personaAssembler;
    private MemoryRetriever memoryRetriever;
    private MemoryStore memoryStore;
    private ToolRegistry toolRegistry;
    private AgentService service;

    @BeforeEach
    void setUp() {
        agentLoop = mock(AgentLoop.class);
        personaProvider = mock(PersonaProvider.class);
        personaAssembler = mock(PersonaAssembler.class);
        memoryRetriever = mock(MemoryRetriever.class);
        memoryStore = mock(MemoryStore.class);
        toolRegistry = mock(ToolRegistry.class);

        service = new AgentService(agentLoop, personaProvider, personaAssembler,
                memoryRetriever, memoryStore, toolRegistry, null, null);

        when(personaProvider.defaultPersona())
                .thenReturn(Persona.of("default", "Assistant", "You are helpful."));
        when(personaAssembler.assemble(any(), any()))
                .thenReturn("You are helpful.");
        when(toolRegistry.listDefinitions())
                .thenReturn(List.of());
    }

    private static AgentResponse completedResponse(String traceId, String content) {
        return new AgentResponse(traceId, content, List.of(), 1, 0,
                AgentResponse.FinishStatus.COMPLETED, "", null);
    }

    @Nested
    @DisplayName("run")
    class Run {
        @Test
        @DisplayName("should assemble system prompt from persona")
        void shouldAssembleSystemPromptFromPersona() {
            when(memoryRetriever.retrieve(any())).thenReturn(List.of());
            when(agentLoop.run(any())).thenReturn(completedResponse("trace-1", "done"));

            service.run("hello", null, null, null, null);

            var captor = ArgumentCaptor.forClass(AgentRequest.class);
            verify(agentLoop).run(captor.capture());
            assertTrue(captor.getValue().systemPrompt().startsWith("You are helpful."));
        }

        @Test
        @DisplayName("should append memories to system prompt")
        void shouldAppendMemoriesToSystemPrompt() {
            var memory = MemoryEntry.semantic("cloud-ai-agent", "user likes Python", 0.8);
            when(memoryRetriever.retrieve(any())).thenReturn(List.of(memory));
            when(agentLoop.run(any())).thenReturn(completedResponse("trace-1", "done"));

            service.run("Python", null, null, null, null);

            var captor = ArgumentCaptor.forClass(AgentRequest.class);
            verify(agentLoop).run(captor.capture());
            var systemPrompt = captor.getValue().systemPrompt();
            assertTrue(systemPrompt.contains("You are helpful."));
            assertTrue(systemPrompt.contains("Relevant Memories"));
            assertTrue(systemPrompt.contains("user likes Python"));
        }

        @Test
        @DisplayName("should persist interaction as working memory after run")
        void shouldPersistInteractionAfterRun() {
            when(memoryRetriever.retrieve(any())).thenReturn(List.of());
            when(agentLoop.run(any())).thenReturn(completedResponse("trace-1", "result text"));

            service.run("hello", null, null, null, "trace-1");

            var captor = ArgumentCaptor.forClass(MemoryEntry.class);
            verify(memoryStore).save(captor.capture());
            assertEquals(MemoryType.WORKING, captor.getValue().type());
            assertEquals("trace-1", captor.getValue().sessionId());
            assertTrue(captor.getValue().content().contains("hello"));
            assertTrue(captor.getValue().content().contains("result text"));
        }

        @Test
        @DisplayName("should not fail when memory persistence throws")
        void shouldNotFailWhenMemoryPersistenceThrows() {
            when(memoryRetriever.retrieve(any())).thenReturn(List.of());
            when(agentLoop.run(any())).thenReturn(completedResponse("trace-1", "done"));
            doThrow(new RuntimeException("store error"))
                    .when(memoryStore).save(any(MemoryEntry.class));

            var response = service.run("hello", null, null, null, "trace-1");
            assertEquals("done", response.content());
        }

        @Test
        @DisplayName("should pass provider and options to AgentRequest")
        void shouldPassProviderAndOptionsToAgentRequest() {
            when(memoryRetriever.retrieve(any())).thenReturn(List.of());
            when(agentLoop.run(any())).thenReturn(completedResponse("trace-1", "done"));

            service.run("hello", "deepseek", 5, Duration.ofMinutes(1), "my-trace");

            var captor = ArgumentCaptor.forClass(AgentRequest.class);
            verify(agentLoop).run(captor.capture());
            var req = captor.getValue();
            assertEquals("deepseek", req.provider());
            assertEquals(5, req.maxTurns());
            assertEquals(Duration.ofMinutes(1), req.timeout());
            assertEquals("my-trace", req.traceId());
        }
    }

    @Nested
    @DisplayName("interrupt and status")
    class InterruptAndStatus {
        @Test
        @DisplayName("should delegate isRunning to AgentLoop")
        void shouldDelegateIsRunning() {
            when(agentLoop.isRunning("trace-1")).thenReturn(true);
            assertTrue(service.isRunning("trace-1"));
        }

        @Test
        @DisplayName("should delegate interrupt to AgentLoop")
        void shouldDelegateInterrupt() {
            service.interrupt("trace-1");
            verify(agentLoop).interrupt("trace-1");
        }
    }

    @Nested
    @DisplayName("input validation")
    class InputValidation {
        @Test
        @DisplayName("should throw on empty prompt via AgentRequest validation")
        void shouldThrowOnEmptyPrompt() {
            when(memoryRetriever.retrieve(any())).thenReturn(List.of());
            assertThrows(IllegalArgumentException.class, () ->
                    service.run("", null, null, null, null));
        }
    }
}
