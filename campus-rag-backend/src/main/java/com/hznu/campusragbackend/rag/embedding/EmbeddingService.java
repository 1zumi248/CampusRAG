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
     * 对分块列表批量向量化，存入向量库
     */
    public void embedAndStore(List<DocumentChunk> chunks, Long documentId) {
        if (chunks == null || chunks.isEmpty()) {
            log.warn("分块列表为空，跳过向量化: documentId={}", documentId);
            return;
        }

        log.info("开始向量化: documentId={}, 共{}块", documentId, chunks.size());
        long startTime = System.currentTimeMillis();

        for (DocumentChunk chunk : chunks) {
            // 1. 生成 embedding
            Embedding embedding = embeddingModel.embed(chunk.getContent()).content();

            // 2. 构建 TextSegment，metadata 用于答案溯源
            TextSegment segment = TextSegment.from(
                    chunk.getContent(),
                    Metadata.from(
                    Map.of(
                            "document_id", documentId.toString(),
                            "chunk_index", chunk.getChunkIndex().toString(),
                            "chunk_db_id", chunk.getId().toString()
                    )
                    )
            );

            // 3. 写入 pgvector（PgVectorEmbeddingStore 的 embeddings 表）
            embeddingStore.add(embedding, segment);
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("向量化完成: documentId={}, 块数={}, 耗时={}ms", documentId, chunks.size(), duration);
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
