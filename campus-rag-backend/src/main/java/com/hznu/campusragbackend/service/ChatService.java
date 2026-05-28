package com.hznu.campusragbackend.service;

import cn.hutool.json.JSONUtil;
import com.hznu.campusragbackend.agent.tools.RagRetrievalTool;
import com.hznu.campusragbackend.model.ChatResponse;
import com.hznu.campusragbackend.model.SourceReference;
import com.hznu.campusragbackend.rag.assistant.RagAssistant;
import dev.langchain4j.service.TokenStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final QueryCache queryCache;
    private final RagAssistant ragAssistant;
    private final RagRetrievalTool ragRetrievalTool;
    private final ConversationService conversationService;

    public ChatResponse chat(String question, Long conversationId) {
        Optional<ChatResponse> cached = queryCache.get(question);
        if (cached.isPresent()) {
            log.info("命中缓存: {}", question);
            ChatResponse cr = cached.get();
            Long effectiveConvId = resolveConversationId(conversationId);
            conversationService.saveMessage(effectiveConvId, question, cr.getAnswer(), cr.getSources());
            cr.setConversationId(effectiveConvId);
            return cr;
        }

        log.info("未命中缓存: {}", question);
        Long effectiveConvId = resolveConversationId(conversationId);
        String answer = ragAssistant.answer(question, effectiveConvId);
        List<SourceReference> sources = ragRetrievalTool.getAndClearSources();
        if (sources == null) sources = List.of();

        conversationService.saveMessage(effectiveConvId, question, answer, sources);
        ChatResponse response = ChatResponse.builder()
                .conversationId(effectiveConvId)
                .answer(answer)
                .sources(sources)
                .build();
        queryCache.put(question, response);
        return response;
    }

    /** 工具名 → 中文描述映射，供前端 SSE tool 事件使用 */
    private static final Map<String, String> TOOL_DISPLAY_NAMES = Map.of(
            "searchKnowledgeBase", "检索知识库",
            "getWeather", "查询天气",
            "getSchedule", "查询课表",
            "getLibrarySeats", "查询图书馆座位",
            "getEmptyClassrooms", "查询空闲教室",
            "getCurrentTime", "获取当前时间"
    );

    public Flux<ServerSentEvent<String>> streamChat(String question, Long conversationId) {
        Optional<ChatResponse> cached = queryCache.get(question);
        Long effectiveConvId = resolveConversationId(conversationId);

        if (cached.isPresent()) {
            log.info("流式命中缓存: {}", question);
            ChatResponse cr = cached.get();
            conversationService.saveMessage(effectiveConvId, question, cr.getAnswer(), cr.getSources());
            return Flux.just(
                    sse("conversation", effectiveConvId.toString()),
                    sse("token", cr.getAnswer()),
                    sse("sources", JSONUtil.toJsonStr(cr.getSources()))
            );
        }

        log.info("流式未命中缓存: {}", question);
        long sseStartTime = System.currentTimeMillis();
        return Flux.<ServerSentEvent<String>>create(sink -> {
            try {
                sink.next(sse("conversation", effectiveConvId.toString()));

                StringBuilder fullAnswer = new StringBuilder();
                java.util.concurrent.atomic.AtomicInteger tokenCount = new java.util.concurrent.atomic.AtomicInteger();

                TokenStream tokenStream = ragAssistant.stream(question, effectiveConvId);
                tokenStream
                        .onPartialResponse(token -> {
                            if (token != null && !token.isEmpty()) {
                                int n = tokenCount.incrementAndGet();
                                log.info("[流式] token#{} +{}ms: {}", n, System.currentTimeMillis() - sseStartTime, token.replace("\n", "\\n"));
                                fullAnswer.append(token);
                                sink.next(sse("token", token));
                            }
                        })
                        .onToolExecuted(te -> {
                            log.info("[流式] 工具调用 tool={} args={}", te.request().name(), te.request().arguments());
                            String displayName = TOOL_DISPLAY_NAMES.getOrDefault(te.request().name(), te.request().name());
                            sink.next(sse("tool", JSONUtil.toJsonStr(Map.of(
                                    "name", te.request().name(),
                                    "displayName", displayName,
                                    "status", "done"
                            ))));
                        })
                        .onCompleteResponse(response -> {
                            log.info("[流式] 完成 +{}ms, 共{}个token", System.currentTimeMillis() - sseStartTime, tokenCount.get());
                            String finalAnswer = fullAnswer.toString();
                            List<SourceReference> sources = ragRetrievalTool.getAndClearSources();
                            if (sources == null) sources = List.of();
                            String sourcesJson = JSONUtil.toJsonStr(sources);

                            conversationService.saveMessage(effectiveConvId, question, finalAnswer, sources);
                            queryCache.put(question, ChatResponse.builder()
                                    .conversationId(effectiveConvId)
                                    .answer(finalAnswer)
                                    .sources(sources)
                                    .build());
                            sink.next(sse("sources", sourcesJson));
                            sink.complete();
                        })
                        .onError(error -> {
                            log.error("TokenStream 错误", error);
                            sink.error(error);
                        })
                        .start();

            } catch (Exception e) {
                log.error("流式问答异常", e);
                sink.error(e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Long resolveConversationId(Long conversationId) {
        return conversationId != null ? conversationId : conversationService.createConversation().getId();
    }

    private static ServerSentEvent<String> sse(String event, String data) {
        return ServerSentEvent.<String>builder().event(event).data(data).build();
    }
}
