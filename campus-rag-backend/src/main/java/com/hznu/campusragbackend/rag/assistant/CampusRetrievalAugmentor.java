package com.hznu.campusragbackend.rag.assistant;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hznu.campusragbackend.model.DocumentChunk;
import com.hznu.campusragbackend.model.SourceReference;
import com.hznu.campusragbackend.rag.retrieval.RetrievalResult;
import com.hznu.campusragbackend.rag.retrieval.RetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 检索增强器，封装检索调用 + 上下文格式化。
 * 每次请求返回独立的 RetrievalContext，无共享状态，天然线程安全。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CampusRetrievalAugmentor {

    private final RetrievalService retrievalService;

    @Value("${rag.retrieval.top-k:5}")
    private int defaultTopK;

    /**
     * 执行检索并将结果格式化为 LLM 上下文文本，同时返回原始检索结果。
     */
    public RetrievalContext retrieveAndFormat(String question) {
        List<RetrievalResult> results = retrievalService.retrieve(question, defaultTopK);

        if (results.isEmpty()) {
            return new RetrievalContext("", List.of());
        }

        List<DocumentChunk> chunks = results.stream()
                .map(RetrievalResult::getChunk)
                .toList();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = chunks.get(i);
            sb.append(String.format("[%d] 《%s》第%d块\n%s\n\n",
                    i + 1,
                    extractDocumentTitle(chunk.getMetadata()),
                    chunk.getChunkIndex(),
                    chunk.getContent()
            ));
        }
        return new RetrievalContext(sb.toString(), results);
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

    /** 将检索结果转换为 SourceReference 列表，元数据解析集中在此处 */
    public List<SourceReference> buildSources(List<RetrievalResult> results) {
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
}
