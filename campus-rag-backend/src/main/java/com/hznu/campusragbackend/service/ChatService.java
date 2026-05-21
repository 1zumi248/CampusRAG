package com.hznu.campusragbackend.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.hznu.campusragbackend.model.ChatResponse;
import com.hznu.campusragbackend.model.SourceReference;
import com.hznu.campusragbackend.rag.assistant.CampusRetrievalAugmentor;
import com.hznu.campusragbackend.rag.assistant.RagAssistant;
import com.hznu.campusragbackend.rag.assistant.RetrievalContext;
import dev.langchain4j.service.TokenStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static com.hznu.campusragbackend.common.Constants.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final StringRedisTemplate redisTemplate;
    private final RagAssistant ragAssistant;
    private final CampusRetrievalAugmentor campusRetrievalAugmentor;
    private final ConversationService conversationService;

    public ChatResponse chat(String question, Long conversationId) {
        String cacheKey = CACHE_KEY_PREFIX + DigestUtil.md5Hex(question.trim());
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.info("命中缓存: {}", question);
            ChatResponse cr = JSONUtil.toBean(cached, ChatResponse.class);
            Long effectiveConvId = resolveConversationId(conversationId);
            conversationService.saveMessage(effectiveConvId, question, cr.getAnswer(), cr.getSources());
            cr.setConversationId(effectiveConvId);
            return cr;
        }

        log.info("未命中缓存: {}", question);
        PreparedCtx ctx = prepareContext(question, conversationId);
        ChatResponse response = doChat(question, ctx);
        redisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(response),
                Duration.ofSeconds(CACHE_EXPIRE_SECONDS));
        return response;
    }

    /**
     * 流式问答。
     * Flux.create() 的 lambda 在订阅后才执行（subscribeOn 切到 boundedElastic 线程），
     * tokenStream.start() 阻塞该线程并逐 token 回调 sink.next()，Spring SSE 每收到一个事件立即 flush。
     */
    public Flux<ServerSentEvent<String>> streamChat(String question, Long conversationId) {
        String cacheKey = CACHE_KEY_PREFIX + DigestUtil.md5Hex(question.trim());
        String cached = redisTemplate.opsForValue().get(cacheKey);
        Long effectiveConvId = resolveConversationId(conversationId);

        if (cached != null) {
            log.info("流式命中缓存: {}", question);
            ChatResponse cr = JSONUtil.toBean(cached, ChatResponse.class);
            conversationService.saveMessage(effectiveConvId, question, cr.getAnswer(), cr.getSources());
            return Flux.just(
                    sse("conversation", effectiveConvId.toString()),
                    sse("token", cr.getAnswer()),
                    sse("sources", JSONUtil.toJsonStr(cr.getSources()))
            );
        }

        log.info("流式未命中缓存: {}", question);
        PreparedCtx ctx = prepareContext(question, conversationId);
        long sseStartTime = System.currentTimeMillis();
        return Flux.<ServerSentEvent<String>>create(sink -> {
            try {
                log.info("[流式] 检索完成 +{}ms", System.currentTimeMillis() - sseStartTime);
                sink.next(sse("conversation", ctx.conversationId().toString()));

                if (ctx.retrievalContext().formattedText().isEmpty()) {
                    String emptyAnswer = "抱歉，知识库中没有找到相关信息。";
                    conversationService.saveMessage(ctx.conversationId(), question, emptyAnswer, List.of());
                    sink.next(sse("token", emptyAnswer));
                    sink.next(sse("sources", "[]"));
                    sink.complete();
                    return;
                }

                List<SourceReference> sources = campusRetrievalAugmentor.buildSources(ctx.retrievalContext().results());
                String sourcesJson = JSONUtil.toJsonStr(sources);
                StringBuilder fullAnswer = new StringBuilder();
                java.util.concurrent.atomic.AtomicInteger tokenCount = new java.util.concurrent.atomic.AtomicInteger();

                TokenStream tokenStream = ragAssistant.stream(ctx.retrievalContext().formattedText(), question, ctx.conversationId());
                tokenStream
                        .onPartialResponse(token -> {
                            if (token != null && !token.isEmpty()) {
                                int n = tokenCount.incrementAndGet();
                                log.info("[流式] token#{} +{}ms: {}", n, System.currentTimeMillis() - sseStartTime, token.replace("\n", "\\n"));
                                fullAnswer.append(token);
                                sink.next(sse("token", token));
                            }
                        })
                        .onCompleteResponse(response -> {
                            log.info("[流式] 完成 +{}ms, 共{}个token", System.currentTimeMillis() - sseStartTime, tokenCount.get());
                            String finalAnswer = fullAnswer.toString();
                            conversationService.saveMessage(ctx.conversationId(), question, finalAnswer, sources);
                            redisTemplate.opsForValue().set(cacheKey,
                                    JSONUtil.toJsonStr(ChatResponse.builder()
                                            .conversationId(ctx.conversationId())
                                            .answer(finalAnswer)
                                            .sources(sources)
                                            .build()),
                                    Duration.ofSeconds(CACHE_EXPIRE_SECONDS));
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

    private record PreparedCtx(Long conversationId, RetrievalContext retrievalContext) {}

    private Long resolveConversationId(Long conversationId) {
        return conversationId != null ? conversationId : conversationService.createConversation().getId();
    }

    private PreparedCtx prepareContext(String question, Long conversationId) {
        Long effectiveConvId = resolveConversationId(conversationId);
        RetrievalContext retrievalCtx = campusRetrievalAugmentor.retrieveAndFormat(question);
        return new PreparedCtx(effectiveConvId, retrievalCtx);
    }

    private ChatResponse doChat(String question, PreparedCtx ctx) {
        if (ctx.retrievalContext().formattedText().isEmpty()) {
            log.warn("未找到相关文档: {}", question);
            String emptyAnswer = "抱歉，知识库中没有找到相关信息。";
            conversationService.saveMessage(ctx.conversationId(), question, emptyAnswer, List.of());
            return ChatResponse.builder()
                    .conversationId(ctx.conversationId())
                    .answer(emptyAnswer)
                    .sources(new ArrayList<>())
                    .build();
        }

        String answer = ragAssistant.answer(ctx.retrievalContext().formattedText(), question, ctx.conversationId());
        List<SourceReference> sources = campusRetrievalAugmentor.buildSources(ctx.retrievalContext().results());
        conversationService.saveMessage(ctx.conversationId(), question, answer, sources);

        log.info("问答处理完成: conversationId={}", ctx.conversationId());
        return ChatResponse.builder()
                .conversationId(ctx.conversationId())
                .answer(answer)
                .sources(sources)
                .build();
    }

    private static ServerSentEvent<String> sse(String event, String data) {
        return ServerSentEvent.<String>builder().event(event).data(data).build();
    }
}
