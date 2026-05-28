package com.hznu.campusragbackend.rag.retrieval;

import com.hznu.campusragbackend.common.exception.RetrievalException;
import com.hznu.campusragbackend.model.ChunkMetadata;
import com.hznu.campusragbackend.model.DocumentChunk;
import com.hznu.campusragbackend.model.SourceReference;
import com.hznu.campusragbackend.repository.DocumentChunkRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class RetrievalService {

    private final EmbeddingModel embeddingModel;
    private final DocumentChunkRepository chunkRepository;
    private final VectorRetrievalStrategy vectorStrategy;
    private final EsRetrievalStrategy esStrategy;
    private final int defaultTopK;

    private static final int RRF_K = 60;

    public RetrievalService(
            EmbeddingModel embeddingModel,
            DocumentChunkRepository chunkRepository,
            VectorRetrievalStrategy vectorStrategy,
            EsRetrievalStrategy esStrategy,
            @Value("${rag.retrieval.top-k:5}") int defaultTopK) {
        this.embeddingModel = embeddingModel;
        this.chunkRepository = chunkRepository;
        this.vectorStrategy = vectorStrategy;
        this.esStrategy = esStrategy;
        this.defaultTopK = defaultTopK;
    }

    public List<RetrievalResult> retrieveVectorOnly(String query, int topK) {
        Embedding queryEmbedding = embed(query);
        Map<Long, Double> idScore = vectorStrategy.search(query, queryEmbedding, topK);
        return buildResults(idScore, topK);
    }

    public List<RetrievalResult> retrieveEsOnly(String query, int topK) {
        Map<Long, Double> idScore = esStrategy.search(query, null, topK);
        return buildResults(idScore, topK);
    }

    public List<RetrievalResult> retrieve(String query, int topK) {
        log.info("混合检索: query={}, topK={}", query, topK);
        Embedding queryEmbedding = embed(query);
        int candidateSize = topK * 2;

        Map<Long, Double> rrfScores = new HashMap<>();
        for (RetrievalStrategy strategy : List.of(vectorStrategy, esStrategy)) {
            try {
                Map<Long, Double> scores = strategy.search(query, queryEmbedding, candidateSize);
                addRrfScores(rrfScores, sortByScoreDesc(scores));
            } catch (Exception e) {
                log.warn("策略 {} 执行失败，降级跳过: {}", strategy.getClass().getSimpleName(), e.getMessage());
            }
        }

        List<Long> topIds = rrfScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(topK)
                .map(Map.Entry::getKey)
                .toList();

        if (topIds.isEmpty()) return List.of();

        Map<Long, DocumentChunk> chunkMap = batchGetChunks(topIds);
        return toResultList(topIds, chunkMap, rrfScores);
    }

    // ────────── 上下文格式化 ──────────

    public RetrievalContext retrieveAndFormat(String question, int topK) {
        List<RetrievalResult> results = retrieve(question, topK);
        if (results.isEmpty()) {
            return new RetrievalContext("", List.of());
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            DocumentChunk chunk = results.get(i).getChunk();
            ChunkMetadata meta = ChunkMetadata.fromJson(chunk.getMetadata());
            sb.append(String.format("[%d] 《%s》第%d块\n%s\n\n",
                    i + 1, meta.documentTitle(), chunk.getChunkIndex(), chunk.getContent()));
        }
        return new RetrievalContext(sb.toString(), results);
    }

    public List<SourceReference> buildSources(List<RetrievalResult> results) {
        return results.stream()
                .map(r -> {
                    DocumentChunk chunk = r.getChunk();
                    ChunkMetadata meta = ChunkMetadata.fromJson(chunk.getMetadata());
                    return SourceReference.builder()
                            .documentId(meta.documentId())
                            .documentTitle(meta.documentTitle())
                            .chunkContent(chunk.getContent())
                            .chunkIndex(chunk.getChunkIndex())
                            .similarityScore(r.getSimilarityScore())
                            .build();
                })
                .toList();
    }

    // ────────── 内部方法 ──────────

    private Embedding embed(String query) {
        try {
            return embeddingModel.embed(query).content();
        } catch (Exception e) {
            throw new RetrievalException("Embedding 模型调用失败", e);
        }
    }

    private List<RetrievalResult> buildResults(Map<Long, Double> idScore, int topK) {
        if (idScore.isEmpty()) return List.of();
        List<Long> sortedIds = sortByScoreDesc(idScore).stream().limit(topK).toList();
        Map<Long, DocumentChunk> chunkMap = batchGetChunks(sortedIds);
        return toResultList(sortedIds, chunkMap, idScore);
    }

    private Map<Long, DocumentChunk> batchGetChunks(List<Long> ids) {
        Map<Long, DocumentChunk> map = new HashMap<>();
        if (!ids.isEmpty()) {
            chunkRepository.selectBatchIds(ids).forEach(c -> map.put(c.getId(), c));
        }
        return map;
    }

    private List<RetrievalResult> toResultList(List<Long> idOrder, Map<Long, DocumentChunk> chunkMap, Map<Long, Double> scores) {
        List<RetrievalResult> results = new ArrayList<>();
        for (Long id : idOrder) {
            DocumentChunk chunk = chunkMap.get(id);
            if (chunk != null) {
                results.add(new RetrievalResult(chunk, scores.getOrDefault(id, 0.0)));
            }
        }
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
            accumulator.merge(rankedIds.get(i), 1.0 / (RRF_K + i + 1), Double::sum);
        }
    }
}