package com.hznu.campusragbackend.rag.retrieval;

import com.hznu.campusragbackend.model.ChunkDocument;
import dev.langchain4j.data.embedding.Embedding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class EsRetrievalStrategy implements RetrievalStrategy {

    private final ElasticsearchOperations esOps;

    @Override
    public Map<Long, Double> search(String query, Embedding queryEmbedding, int maxResults) {
        Map<Long, Double> idScore = new HashMap<>();
        try {
            NativeQuery q = NativeQuery.builder()
                    .withQuery(m -> m.match(b -> b.field("content").query(query)))
                    .withMaxResults(maxResults)
                    .build();
            for (SearchHit<ChunkDocument> hit : esOps.search(q, ChunkDocument.class).getSearchHits()) {
                Long id = Long.parseLong(hit.getContent().getId());
                idScore.merge(id, (double) hit.getScore(), Math::max);
            }
        } catch (Exception e) {
            log.warn("ES检索失败（降级跳过）: {}", e.getMessage());
        }
        return idScore;
    }
}
