package com.hznu.campusragbackend.service;

import com.hznu.campusragbackend.model.ChunkDocument;
import com.hznu.campusragbackend.model.ChunkMetadata;
import com.hznu.campusragbackend.model.DocumentChunk;
import com.hznu.campusragbackend.rag.retrieval.RetrievalResult;
import com.hznu.campusragbackend.rag.retrieval.RetrievalService;
import com.hznu.campusragbackend.repository.ChunkSearchRepository;
import com.hznu.campusragbackend.repository.DocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final DocumentChunkRepository documentChunkRepository;
    private final ChunkSearchRepository chunkSearchRepository;
    private final RetrievalService retrievalService;

    public Map<String, Object> compare(String query) {
        int topK = 5;
        List<RetrievalResult> vectorOnly = retrievalService.retrieveVectorOnly(query, topK);
        List<RetrievalResult> esOnly = retrievalService.retrieveEsOnly(query, topK);
        List<RetrievalResult> hybrid = retrievalService.retrieve(query, topK);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", query);
        result.put("vectorOnly", formatResults(vectorOnly));
        result.put("esOnly", formatResults(esOnly));
        result.put("hybrid", formatResults(hybrid));
        return result;
    }

    private List<Map<String, Object>> formatResults(List<RetrievalResult> results) {
        List<Map<String, Object>> list = new ArrayList<>();
        int rank = 0;
        for (RetrievalResult r : results) {
            rank++;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("rank", rank);
            item.put("chunkId", r.getChunk().getId());
            ChunkMetadata meta = ChunkMetadata.fromJson(r.getChunk().getMetadata());
            item.put("documentTitle", meta.documentTitle());
            String content = r.getChunk().getContent();
            item.put("preview", content.length() > 80 ? content.substring(0, 80) + "..." : content);
            item.put("score", String.format("%.4f", r.getSimilarityScore()));
            list.add(item);
        }
        return list;
    }

    public Map<String, Object> migrateChunksToEs() {
        List<DocumentChunk> allChunks = documentChunkRepository.selectList(null);
        if (allChunks.isEmpty()) {
            return Map.of("message", "没有需要迁移的数据", "count", 0);
        }

        List<ChunkDocument> esDocs = new ArrayList<>();
        for (DocumentChunk chunk : allChunks) {
            ChunkMetadata meta = ChunkMetadata.fromJson(chunk.getMetadata());
            esDocs.add(ChunkDocument.builder()
                    .id(chunk.getId().toString())
                    .documentId(chunk.getDocumentId())
                    .content(chunk.getContent())
                    .documentTitle(meta.documentTitle())
                    .chunkIndex(chunk.getChunkIndex())
                    .chunkType(meta.chunkType())
                    .sectionPath(meta.sectionPath())
                    .build());
        }

        chunkSearchRepository.saveAll(esDocs);
        log.info("ES迁移完成: {}条", esDocs.size());

        Map<String, Object> result = new HashMap<>();
        result.put("message", "迁移完成");
        result.put("count", esDocs.size());
        return result;
    }
}