package com.hznu.campusragbackend.service;

import com.hznu.campusragbackend.agent.tools.RagRetrievalTool;
import com.hznu.campusragbackend.model.Conversation;
import com.hznu.campusragbackend.rag.assistant.RagAssistant;
import dev.langchain4j.service.TokenStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private QueryCache queryCache;
    @Mock
    private RagAssistant ragAssistant;
    @Mock
    private RagRetrievalTool ragRetrievalTool;
    @Mock
    private ConversationService conversationService;
    @Mock
    private TokenStream tokenStream;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(queryCache, ragAssistant, ragRetrievalTool, conversationService);
    }

    @Test
    void streamChatEmitsErrorEventAndCompletesWhenModelConnectionFails() {
        when(queryCache.get("question")).thenReturn(Optional.empty());
        when(conversationService.createConversation()).thenReturn(
                Conversation.builder().id(42L).title("新建会话").build());
        when(ragAssistant.stream("question", 42L)).thenReturn(tokenStream);
        when(tokenStream.onPartialResponse(any())).thenReturn(tokenStream);
        when(tokenStream.onToolExecuted(any())).thenReturn(tokenStream);
        when(tokenStream.onCompleteResponse(any())).thenReturn(tokenStream);

        AtomicReference<Consumer<Throwable>> errorHandler = new AtomicReference<>();
        when(tokenStream.onError(any())).thenAnswer(invocation -> {
            errorHandler.set(invocation.getArgument(0));
            return tokenStream;
        });
        doAnswer(invocation -> {
            errorHandler.get().accept(new ResourceAccessException("DashScope TLS handshake failed"));
            return null;
        }).when(tokenStream).start();

        List<ServerSentEvent<String>> events = chatService.streamChat("question", null)
                .collectList()
                .block(Duration.ofSeconds(2));

        assertThat(events).isNotNull();
        assertThat(events).extracting(ServerSentEvent::event)
                .containsExactly("conversation", "error");
        assertThat(events.get(1).data()).contains("网络或代理");
    }
}
