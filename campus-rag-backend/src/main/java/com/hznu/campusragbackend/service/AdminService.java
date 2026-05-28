package com.hznu.campusragbackend.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hznu.campusragbackend.model.ChunkDocument;
import com.hznu.campusragbackend.model.DocumentChunk;
import com.hznu.campusragbackend.repository.ChunkSearchRepository;
import com.hznu.campusragbackend.repository.DocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final DocumentChunkRepository documentChunkRepository;
    private final ChunkSearchRepository chunkSearchRepository;

    public Map<String, Object> migrateChunksToEs() {
        List<DocumentChunk> allChunks = documentChunkRepository.selectList(null);
        if (allChunks.isEmpty()) {
            return Map.of("message", "没有需要迁移的数据", "count", 0);
        }

        List<ChunkDocument> esDocs = new ArrayList<>();
        for (DocumentChunk chunk : allChunks) {
            JSONObject meta = JSONUtil.parseObj(chunk.getMetadata());
            esDocs.add(ChunkDocument.builder()
                    .id(chunk.getId().toString())
                    .documentId(chunk.getDocumentId())
                    .content(chunk.getContent())
                    .documentTitle(meta.getStr("document_title"))
                    .chunkIndex(chunk.getChunkIndex())
                    .chunkType(meta.getStr("chunk_type"))
                    .sectionPath(meta.getStr("section_path"))
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