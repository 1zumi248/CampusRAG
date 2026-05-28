package com.hznu.campusragbackend.rag.retrieval;

import com.hznu.campusragbackend.common.exception.RetrievalException;
import com.hznu.campusragbackend.model.ChunkDocument;
import com.hznu.campusragbackend.model.DocumentChunk;
import com.hznu.campusragbackend.repository.DocumentChunkRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class RetrievalService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final DocumentChunkRepository chunkRepository;
    private final ElasticsearchOperations esOps;
    private final double minScore;
    private final int retrievalTopK;

    /** RRF 融合常数，平衡排名和分数 */
    private static final int RRF_K = 60;

    public RetrievalService(
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore,
            DocumentChunkRepository chunkRepository,
            ElasticsearchOperations esOps,
            @Value("${rag.retrieval.min-score:0.4}") double minScore,
            @Value("${rag.retrieval.top-k:5}") int retrievalTopK) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.chunkRepository = chunkRepository;
        this.esOps = esOps;
        this.minScore = minScore;
        this.retrievalTopK = retrievalTopK;
    }

    /**
     * 混合检索：向量检索 + ES全文检索 → RRF融合
     * @param query 用户问题
     * @param topK 返回最相关的 K 条
     * @return 检索结果
     */
    public List<RetrievalResult> retrieve(String query, int topK) {
        log.info("开始混合检索: query={}, topK={}, minScore={}", query, topK, minScore);

        // 1. Embed query
        Embedding queryEmbedding;
        try {
            queryEmbedding = embeddingModel.embed(query).content();
        } catch (Exception e) {
            throw new RetrievalException("Embedding 模型调用失败", e);
        }

        // 2. 多捞一些候选用做RRF融合
        int candidateSize = topK * 2;

        // pgvector 向量检索
        Map<Long, Double> vectorIdScore = new HashMap<>();
        try {
            EmbeddingSearchResult<TextSegment> result = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(candidateSize)
                    .minScore(minScore)
                    .build()
            );
            for (EmbeddingMatch<TextSegment> match : result.matches()) {
                Long id = Long.parseLong(match.embedded().metadata().getString("chunk_db_id"));
                vectorIdScore.merge(id, match.score(), Math::max);
            }
        } catch (Exception e) {
            log.warn("向量检索失败: {}", e.getMessage());
        }
        log.info("向量检索: {}条", vectorIdScore.size());

        // ES 全文检索
        Map<Long, Double> esIdScore = new HashMap<>();
        try {
            NativeQuery queryEs = NativeQuery.builder()
                    .withQuery(q -> q.match(m -> m.field("content").query(query)))
                    .withMaxResults(candidateSize)
                    .build();
            List<SearchHit<ChunkDocument>> hits = esOps.search(queryEs, ChunkDocument.class).getSearchHits();
            for (int i = 0; i < hits.size(); i++) {
                SearchHit<ChunkDocument> hit = hits.get(i);
                Long id = Long.parseLong(hit.getContent().getId());
                esIdScore.merge(id, (double) hit.getScore(), Math::max);
            }
        } catch (Exception e) {
            log.warn("ES检索失败: {}", e.getMessage());
        }
        log.info("ES检索: {}条", esIdScore.size());

        // 3. RRF融合
        Map<Long, Double> rrfScores = new HashMap<>();
        addRrfScores(rrfScores, sortByScoreDesc(vectorIdScore));
        addRrfScores(rrfScores, sortByScoreDesc(esIdScore));

        // 4. 按RRF分排序，取topK
        List<Long> topChunkIds = rrfScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(topK)
                .map(Map.Entry::getKey)
                .toList();

        if (topChunkIds.isEmpty()) {
            return List.of();
        }

        // 5. 批量查询pg获取完整chunk
        List<DocumentChunk> chunks = chunkRepository.selectBatchIds(topChunkIds);

        // 6. 保留RRF排序
        Map<Long, DocumentChunk> chunkMap = new HashMap<>();
        chunks.forEach(c -> chunkMap.put(c.getId(), c));

        List<RetrievalResult> results = new ArrayList<>();
        for (Long id : topChunkIds) {
            DocumentChunk chunk = chunkMap.get(id);
            if (chunk != null) {
                results.add(new RetrievalResult(chunk, rrfScores.getOrDefault(id, 0.0)));
            }
        }

        log.info("混合检索完成: 向量{}条 + ES{}条 → RRF融合后返回{}条",
                vectorIdScore.size(), esIdScore.size(), results.size());
        return results;
    }

    private List<Long> sortByScoreDesc(Map<Long, Double> idScoreMap) {
        return idScoreMap.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();
    }

    private void addRrfScores(Map<Long, Double> accumulator, List<Long> rankedIds) {
        for (int i = 0; i < rankedIds.size(); i++) {
            double rrf = 1.0 / (RRF_K + i + 1);
            accumulator.merge(rankedIds.get(i), rrf, Double::sum);
        }
    }
}