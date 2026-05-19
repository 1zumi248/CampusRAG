package com.hznu.campusragbackend.rag.embedding;

import com.hznu.campusragbackend.model.DocumentChunk;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    /**
     * 对分块列表批量向量化，存入向量库。
     * 使用 embedAll + addAll 替代逐条调用，减少 HTTP 往返次数。
     */
    public void embedAndStore(List<DocumentChunk> chunks, Long documentId) {
        if (chunks == null || chunks.isEmpty()) {
            log.warn("分块列表为空，跳过向量化: documentId={}", documentId);
            return;
        }

        log.info("开始批量向量化: documentId={}, 共{}块", documentId, chunks.size());
        long startTime = System.currentTimeMillis();

        List<TextSegment> segments = chunks.stream()
                .map(chunk -> TextSegment.from(
                        chunk.getContent(),
                        Metadata.from(Map.of(
                                "document_id", documentId.toString(),
                                "chunk_index", chunk.getChunkIndex().toString(),
                                "chunk_db_id", chunk.getId().toString()
                        ))
                ))
                .toList();

        // DashScope text-embedding-v4 limits batch size to 10
        int batchSize = 10;
        for (int i = 0; i < segments.size(); i += batchSize) {
            List<TextSegment> batch = segments.subList(i, Math.min(i + batchSize, segments.size()));
            List<Embedding> embeddings = embeddingModel.embedAll(batch).content();
            embeddingStore.addAll(embeddings, batch);
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("批量向量化完成: documentId={}, 块数={}, 耗时={}ms", documentId, chunks.size(), duration);
    }

    /**
     * 删除指定文档的所有向量
     */
    public void deleteByDocumentId(Long documentId) {
        Filter filter = MetadataFilterBuilder.metadataKey("document_id").isEqualTo(documentId.toString());
        embeddingStore.removeAll(filter);
        log.info("已删除向量: documentId={}", documentId);
    }
}
