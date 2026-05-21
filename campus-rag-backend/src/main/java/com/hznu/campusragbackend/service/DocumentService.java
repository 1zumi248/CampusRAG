package com.hznu.campusragbackend.service;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hznu.campusragbackend.common.exception.DocumentNotFoundException;
import com.hznu.campusragbackend.common.exception.DocumentParseException;
import com.hznu.campusragbackend.model.Document;
import com.hznu.campusragbackend.model.DocumentChunk;
import com.hznu.campusragbackend.rag.chunk.ChunkService;
import com.hznu.campusragbackend.rag.embedding.EmbeddingService;
import com.hznu.campusragbackend.rag.parser.DocumentParserService;
import com.hznu.campusragbackend.rag.parser.ParsedDocumentResult;
import com.hznu.campusragbackend.repository.DocumentRepository;
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
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentParserService documentParserService;
    private final ChunkService chunkService;
    private final EmbeddingService embeddingService;

    @Transactional
    public Document uploadDocument(MultipartFile file) {
        ParsedDocumentResult parsedResult;
        try {
            parsedResult = documentParserService.parse(file);
        } catch (Exception e) {
            throw new DocumentParseException(file.getOriginalFilename(), e);
        }

        log.info("开始处理文件: {}, 类型: {}, 大小: {} bytes",
                parsedResult.getFileName(),
                parsedResult.getFileType(),
                parsedResult.getFileSize());

        String contentHash = DigestUtil.md5Hex(parsedResult.getContent());

        Document existingDoc = documentRepository.selectOne(
            new LambdaQueryWrapper<Document>()
                .eq(Document::getContentHash, contentHash)
        );

        if (existingDoc != null) {
            log.warn("文档已存在: {}", existingDoc.getTitle());
            return existingDoc;
        }

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

        documentRepository.insert(document);

        List<DocumentChunk> chunks;
        if (!parsedResult.getContentBlocks().isEmpty()) {
            chunks = chunkService.chunk(parsedResult.getContentBlocks(), document.getId(), document.getTitle());
        } else {
            chunks = chunkService.chunk(parsedResult.getPlainText(), document.getId(), document.getTitle());
        }

        embeddingService.embedAndStore(chunks, document.getId());

        log.info("文档上传成功: ID={}, 标题={}, 分块数={}", document.getId(), document.getTitle(), chunks.size());
        return document;
    }

    public List<Document> listDocuments() {
        return documentRepository.selectList(null);
    }

    public Document getDocumentById(Long id) {
        Document document = documentRepository.selectById(id);
        if (document == null) {
            throw new DocumentNotFoundException(id);
        }
        return document;
    }

    @Transactional
    public void deleteDocument(Long id) {
        embeddingService.deleteByDocumentId(id);
        chunkService.deleteByDocumentId(id);
        documentRepository.deleteById(id);
        log.info("文档已删除: ID={}", id);
    }
}
