package com.hznu.campusragbackend.rag.retrieval;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class VectorRetrievalStrategy implements RetrievalStrategy {

    private final EmbeddingStore<TextSegment> embeddingStore;

    @Value("${rag.retrieval.min-score:0.4}")
    private double minScore;

    @Override
    public Map<Long, Double> search(String query, Embedding queryEmbedding, int maxResults) {
        Map<Long, Double> idScore = new HashMap<>();
        try {
            EmbeddingSearchResult<TextSegment> result = embeddingStore.search(
                    EmbeddingSearchRequest.builder()
                            .queryEmbedding(queryEmbedding)
                            .maxResults(maxResults)
                            .minScore(minScore)
                            .build()
            );
            for (EmbeddingMatch<TextSegment> match : result.matches()) {
                Long id = Long.parseLong(match.embedded().metadata().getString("chunk_db_id"));
                idScore.merge(id, match.score(), Math::max);
            }
        } catch (Exception e) {
            log.warn("向量检索失败: {}", e.getMessage());
        }
        return idScore;
    }
}
