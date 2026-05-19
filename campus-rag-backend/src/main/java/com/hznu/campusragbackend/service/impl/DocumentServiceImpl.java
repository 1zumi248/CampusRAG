package com.hznu.campusragbackend.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hznu.campusragbackend.model.Document;
import com.hznu.campusragbackend.model.DocumentChunk;
import com.hznu.campusragbackend.rag.chunk.ChunkService;
import com.hznu.campusragbackend.rag.embedding.EmbeddingService;
import com.hznu.campusragbackend.rag.parser.DocumentParserService;
import com.hznu.campusragbackend.rag.parser.ParsedDocumentResult;
import com.hznu.campusragbackend.repository.DocumentRepository;
import com.hznu.campusragbackend.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentParserService documentParserService;
    private final ChunkService chunkService;
    private final EmbeddingService embeddingService;

    @Override
    @Transactional
    public Document uploadDocument(MultipartFile file) {
        // 1. 解析文档
        ParsedDocumentResult parsedResult = documentParserService.parse(file);

        log.info("开始处理文件: {}, 类型: {}, 大小: {} bytes",
                parsedResult.getFileName(),
                parsedResult.getFileType(),
                parsedResult.getFileSize());

        // 2. 计算内容哈希（用于检测重复）
        String contentHash = DigestUtil.md5Hex(parsedResult.getContent());

        // 3. 检查是否已存在相同内容的文档
        Document existingDoc = documentRepository.selectOne(
            new LambdaQueryWrapper<Document>()
                .eq(Document::getContentHash, contentHash)
        );

        if (existingDoc != null) {
            log.warn("文档已存在: {}", existingDoc.getTitle());
            return existingDoc;
        }

        // 4. 创建文档对象
        Document document = Document.builder()
                .title(parsedResult.getFileName())
                .fileName(parsedResult.getFileName())
                .fileType(parsedResult.getFileType())
                .fileSize(parsedResult.getFileSize())
                .content(parsedResult.getContent())
                .contentHash(contentHash)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // 5. 保存到数据库
        documentRepository.insert(document);

        // 6. 分块并持久化到 document_chunks
        List<DocumentChunk> chunks = chunkService.chunk(
                document.getContent(),
                document.getId(),
                document.getTitle()
        );

        // 7. 向量化并存入向量库（最后执行：独立连接池，失败时上面操作回滚）
        embeddingService.embedAndStore(chunks, document.getId());

        log.info("文档上传成功: ID={}, 标题={}, 分块数={}", document.getId(), document.getTitle(), chunks.size());
        return document;
    }

    @Override
    public List<Document> listDocuments() {
        return documentRepository.selectList(null);
    }

    @Override
    public Document getDocumentById(Long id) {
        return documentRepository.selectById(id);
    }

    @Override
    @Transactional
    public void deleteDocument(Long id) {
        // 1. 先删向量（独立连接池，先处理）
        embeddingService.deleteByDocumentId(id);

        // 2. 删分块（MyBatis，参与当前事务）
        chunkService.deleteByDocumentId(id);

        // 3. 删文档记录（MyBatis，参与当前事务）
        documentRepository.deleteById(id);

        log.info("文档已删除: ID={}", id);
    }
}
