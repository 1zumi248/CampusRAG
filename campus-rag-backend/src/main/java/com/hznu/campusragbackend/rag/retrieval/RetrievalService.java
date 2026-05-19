package com.hznu.campusragbackend.rag.retrieval;

import com.hznu.campusragbackend.model.DocumentChunk;
import com.hznu.campusragbackend.repository.DocumentChunkRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RetrievalService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final DocumentChunkRepository chunkRepository;

    @Value("${rag.retrieval.min-score:0.4}")
    private double minScore;

    /**
     * 根据用户问题检索相关文档片段
     * @param query 用户问题
     * @param topK 返回最相关的 K 条
     * @return 检索结果（含相关度分数）
     */
    public List<RetrievalResult> retrieve(String query, int topK) {
        log.info("开始检索: query={}, topK={}, minScore={}", query, topK, minScore);

        // 1. 将问题转换为 embedding
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        // 2. 在向量库中检索 Top-K 相似片段
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(
            EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(topK)
                .minScore(minScore)
                .build()
        );
        List<EmbeddingMatch<TextSegment>> matches = result.matches();

        log.info("检索到 {} 个符合条件的结果", matches.size());

        if (matches.isEmpty()) {
            return List.of();
        }

        // 3. 收集 chunk_db_id 和对应的分数
        Map<Long, Double> scoreMap = matches.stream().collect(
            Collectors.toMap(
                m -> Long.parseLong(m.embedded().metadata().getString("chunk_db_id")),
                EmbeddingMatch::score,
                (a, b) -> a  // 去重：同一 chunk 保留第一个
            )
        );

        // 4. 批量查询（一次 SQL 替代 N+1）
        List<DocumentChunk> chunks = chunkRepository.selectBatchIds(new ArrayList<>(scoreMap.keySet()));

        // 5. 组装结果，按相似度降序排列
        return chunks.stream()
                .map(chunk -> new RetrievalResult(chunk, scoreMap.getOrDefault(chunk.getId(), 0.0)))
                .sorted((a, b) -> Double.compare(b.getSimilarityScore(), a.getSimilarityScore()))
                .collect(Collectors.toList());
    }
}
