package com.hznu.campusragbackend.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hznu.campusragbackend.model.ChatResponse;
import com.hznu.campusragbackend.model.DocumentChunk;
import com.hznu.campusragbackend.model.SourceReference;
import com.hznu.campusragbackend.rag.assistant.CampusRetrievalAugmentor;
import com.hznu.campusragbackend.rag.assistant.RagAssistant;
import com.hznu.campusragbackend.rag.assistant.RetrievalContext;
import com.hznu.campusragbackend.rag.retrieval.RetrievalResult;
import dev.langchain4j.service.TokenStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

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

    /**
     * RAG 问答（同步），支持会话上下文。
     * 始终以问题文本为 key 先查 Redis，避免同一问题重复调用 LLM。
     */
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
        Long effectiveConvId = resolveConversationId(conversationId);
        ChatResponse response = doChat(question, effectiveConvId);
        redisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(response),
                Duration.ofSeconds(CACHE_EXPIRE_SECONDS));
        return response;
    }

    /**
     * RAG 问答（流式 SSE），支持会话上下文。
     * 始终以问题文本为 key 先查 Redis，避免同一问题重复调用 LLM。
     */
    public Flux<ServerSentEvent<String>> streamChat(String question, Long conversationId) {
        // 始终先查 Redis 缓存（以问题文本为 key）
        String cacheKey = CACHE_KEY_PREFIX + DigestUtil.md5Hex(question.trim());
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.info("流式命中缓存: {}", question);
            ChatResponse cr = JSONUtil.toBean(cached, ChatResponse.class);
            Long effectiveConvId = resolveConversationId(conversationId);
            conversationService.saveMessage(effectiveConvId, question, cr.getAnswer(), cr.getSources());
            cr.setConversationId(effectiveConvId);
            return Flux.just(
                    ServerSentEvent.<String>builder().event("conversation").data(effectiveConvId.toString()).build(),
                    ServerSentEvent.<String>builder().event("token").data(cr.getAnswer()).build(),
                    ServerSentEvent.<String>builder().event("sources")
                            .data(JSONUtil.toJsonStr(cr.getSources())).build()
            );
        }

        log.info("流式未命中缓存: {}", question);
        Long effectiveConvId = resolveConversationId(conversationId);
        return doStreamChat(question, effectiveConvId, cacheKey);
    }

    /** conversationId 为 null 时创建新会话，否则直接返回 */
    private Long resolveConversationId(Long conversationId) {
        if (conversationId != null) {
            return conversationId;
        }
        return conversationService.createConversation().getId();
    }

    /** 核心同步问答（不走缓存），带 conversationId */
    private ChatResponse doChat(String question, Long conversationId) {
        RetrievalContext ctx = campusRetrievalAugmentor.retrieveAndFormat(question);

        if (ctx.formattedText().isEmpty()) {
            log.warn("未找到相关文档: {}", question);
            String emptyAnswer = "抱歉，知识库中没有找到相关信息。";
            conversationService.saveMessage(conversationId, question, emptyAnswer, List.of());
            return ChatResponse.builder()
                    .conversationId(conversationId)
                    .answer(emptyAnswer)
                    .sources(new ArrayList<>())
                    .build();
        }

        String answer = ragAssistant.answer(ctx.formattedText(), question, conversationId);
        List<SourceReference> sources = buildSources(ctx.results());

        conversationService.saveMessage(conversationId, question, answer, sources);

        log.info("问答处理完成: conversationId={}", conversationId);
        return ChatResponse.builder()
                .conversationId(conversationId)
                .answer(answer)
                .sources(sources)
                .build();
    }

    /** 核心流式问答（不走缓存），带 conversationId。cacheKey 非 null 时流完成后写入 Redis */
    private Flux<ServerSentEvent<String>> doStreamChat(String question, Long conversationId, String cacheKey) {
        RetrievalContext ctx = campusRetrievalAugmentor.retrieveAndFormat(question);

        if (ctx.formattedText().isEmpty()) {
            String emptyAnswer = "抱歉，知识库中没有找到相关信息。";
            conversationService.saveMessage(conversationId, question, emptyAnswer, List.of());
            return Flux.just(
                    ServerSentEvent.<String>builder().event("conversation").data(conversationId.toString()).build(),
                    ServerSentEvent.<String>builder().event("token").data(emptyAnswer).build(),
                    ServerSentEvent.<String>builder().event("sources").data("[]").build()
            );
        }

        List<SourceReference> sources = buildSources(ctx.results());
        String sourcesJson = JSONUtil.toJsonStr(sources);

        TokenStream tokenStream = ragAssistant.stream(ctx.formattedText(), question, conversationId);
        StringBuilder fullAnswer = new StringBuilder();
        return Flux.<ServerSentEvent<String>>create(sink -> {
            // 先发送 conversationId
            sink.next(ServerSentEvent.<String>builder().event("conversation").data(conversationId.toString()).build());

            tokenStream
                    .onPartialResponse(token -> {
                        fullAnswer.append(token);
                        sink.next(
                                ServerSentEvent.<String>builder().event("token").data(token).build());
                    })
                    .onCompleteResponse(response -> {
                        String finalAnswer = fullAnswer.toString();
                        conversationService.saveMessage(conversationId, question, finalAnswer, sources);

                        // 流完成后写 Redis 缓存（仅独立查询模式）
                        if (cacheKey != null) {
                            ChatResponse cacheEntry = ChatResponse.builder()
                                    .conversationId(conversationId)
                                    .answer(finalAnswer)
                                    .sources(sources)
                                    .build();
                            redisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(cacheEntry),
                                    Duration.ofSeconds(CACHE_EXPIRE_SECONDS));
                        }

                        sink.next(ServerSentEvent.<String>builder().event("sources").data(sourcesJson).build());
                        sink.complete();
                    })
                    .onError(sink::error)
                    .start();

            sink.onCancel(() -> log.debug("SSE 客户端断开: {}", question));
        }).timeout(Duration.ofSeconds(60));
    }

    private List<SourceReference> buildSources(List<RetrievalResult> results) {
        return results.stream()
                .map(r -> {
                    DocumentChunk chunk = r.getChunk();
                    return SourceReference.builder()
                            .documentId(extractDocumentId(chunk.getMetadata()))
                            .documentTitle(extractDocumentTitle(chunk.getMetadata()))
                            .chunkContent(chunk.getContent())
                            .chunkIndex(chunk.getChunkIndex())
                            .similarityScore(r.getSimilarityScore())
                            .build();
                })
                .toList();
    }

    private String extractDocumentTitle(String metadataJson) {
        try {
            JSONObject meta = JSONUtil.parseObj(metadataJson);
            return meta.getStr("document_title", "未知文档");
        } catch (Exception e) {
            log.warn("解析元数据失败: {}", metadataJson, e);
            return "未知文档";
        }
    }

    private Long extractDocumentId(String metadataJson) {
        try {
            JSONObject meta = JSONUtil.parseObj(metadataJson);
            String docIdStr = meta.getStr("document_id");
            return docIdStr != null ? Long.valueOf(docIdStr) : null;
        } catch (Exception e) {
            log.warn("解析文档ID失败: {}", metadataJson, e);
            return null;
        }
    }
}
