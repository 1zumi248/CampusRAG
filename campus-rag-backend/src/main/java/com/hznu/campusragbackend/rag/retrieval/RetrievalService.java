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
        Map<Long, Double> vectorScores = new HashMap<>();
        Map<Long, Double> esScores = new HashMap<>();
        Map<Long, Integer> vectorRanks = new HashMap<>();
        Map<Long, Integer> esRanks = new HashMap<>();

        // 向量检索
        try {
            Map<Long, Double> scores = vectorStrategy.search(query, queryEmbedding, candidateSize);
            vectorScores.putAll(scores);
            List<Long> ranked = sortByScoreDesc(scores);
            for (int i = 0; i < ranked.size(); i++) {
                vectorRanks.put(ranked.get(i), i + 1);
            }
            addRrfScores(rrfScores, ranked);
        } catch (Exception e) {
            log.warn("向量检索失败，降级跳过: {}", e.getMessage());
        }

        // ES 检索
        try {
            Map<Long, Double> scores = esStrategy.search(query, null, candidateSize);
            esScores.putAll(scores);
            List<Long> ranked = sortByScoreDesc(scores);
            for (int i = 0; i < ranked.size(); i++) {
                esRanks.put(ranked.get(i), i + 1);
            }
            addRrfScores(rrfScores, ranked);
        } catch (Exception e) {
            log.warn("ES检索失败，降级跳过: {}", e.getMessage());
        }

        List<Long> topIds = rrfScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(topK)
                .map(Map.Entry::getKey)
                .toList();

        if (topIds.isEmpty()) return List.of();

        Map<Long, DocumentChunk> chunkMap = batchGetChunks(topIds);
        return toResultList(topIds, chunkMap, rrfScores, vectorScores, esScores, vectorRanks, esRanks);
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
        return buildSources(results, false);
    }

    public List<SourceReference> buildSources(List<RetrievalResult> results, boolean withDebug) {
        return results.stream()
                .map(r -> {
                    DocumentChunk chunk = r.getChunk();
                    ChunkMetadata meta = ChunkMetadata.fromJson(chunk.getMetadata());
                    SourceReference.SourceReferenceBuilder b = SourceReference.builder()
                            .documentId(meta.documentId())
                            .documentTitle(meta.documentTitle())
                            .chunkContent(chunk.getContent())
                            .chunkIndex(chunk.getChunkIndex())
                            .similarityScore(r.getSimilarityScore());
                    if (withDebug) {
                        b.vectorScore(r.getVectorScore())
                         .esScore(r.getEsScore())
                         .vectorRank(r.getVectorRank())
                         .esRank(r.getEsRank());
                    }
                    return b.build();
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
        List<RetrievalResult> results = new ArrayList<>();
        for (Long id : sortedIds) {
            DocumentChunk chunk = chunkMap.get(id);
            if (chunk != null) {
                results.add(new RetrievalResult(chunk, idScore.getOrDefault(id, 0.0)));
            }
        }
        return results;
    }

    private Map<Long, DocumentChunk> batchGetChunks(List<Long> ids) {
        Map<Long, DocumentChunk> map = new HashMap<>();
        if (!ids.isEmpty()) {
            chunkRepository.selectBatchIds(ids).forEach(c -> map.put(c.getId(), c));
        }
        return map;
    }

    private List<RetrievalResult> toResultList(
            List<Long> idOrder,
            Map<Long, DocumentChunk> chunkMap,
            Map<Long, Double> rrfScores,
            Map<Long, Double> vectorScores,
            Map<Long, Double> esScores,
            Map<Long, Integer> vectorRanks,
            Map<Long, Integer> esRanks) {
        List<RetrievalResult> results = new ArrayList<>();
        for (Long id : idOrder) {
            DocumentChunk chunk = chunkMap.get(id);
            if (chunk != null) {
                results.add(new RetrievalResult(
                        chunk,
                        rrfScores.getOrDefault(id, 0.0),
                        vectorScores.getOrDefault(id, 0.0),
                        esScores.getOrDefault(id, 0.0),
                        vectorRanks.getOrDefault(id, 0),
                        esRanks.getOrDefault(id, 0)
                ));
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