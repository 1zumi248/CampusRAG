package com.hznu.campusragbackend.rag.chunk;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hznu.campusragbackend.common.Constants;
import com.hznu.campusragbackend.model.ChunkDocument;
import com.hznu.campusragbackend.model.DocumentChunk;
import com.hznu.campusragbackend.rag.parser.ContentBlock;
import com.hznu.campusragbackend.repository.ChunkSearchRepository;
import com.hznu.campusragbackend.repository.DocumentChunkRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChunkService {

    private final DocumentChunkRepository documentChunkRepository;
    private final ChunkSearchRepository chunkSearchRepository;

    public ChunkService(DocumentChunkRepository documentChunkRepository,
                        ChunkSearchRepository chunkSearchRepository) {
        this.documentChunkRepository = documentChunkRepository;
        this.chunkSearchRepository = chunkSearchRepository;
    }

    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[。！？；\\n])\\s*");

    /**
     * 对文本进行分块，存入 document_chunks 表，返回分块列表
     */
    public List<DocumentChunk> chunk(String text, Long documentId, String documentTitle) {
        if (text == null || text.isBlank()) {
            log.warn("文本为空，跳过分块: documentId={}", documentId);
            return List.of();
        }

        // 第一步：按段落粗切
        List<String> paragraphs = splitByParagraph(text);

        // 第二步：段落细切 + 合并短块
        List<String> rawChunks = refineChunks(paragraphs);

        // 第三步：持久化到 document_chunks 表（批量插入）
        List<DocumentChunk> chunks = new ArrayList<>();
        for (int i = 0; i < rawChunks.size(); i++) {
            chunks.add(DocumentChunk.builder()
                    .documentId(documentId)
                    .chunkIndex(i)
                    .content(rawChunks.get(i))
                    .metadata(buildMetadata(documentId, documentTitle, i))
                    .build());
        }

        if (!chunks.isEmpty()) {
            documentChunkRepository.insertBatch(chunks);
            // 批量插入不回填主键，查回带 ID 的完整列表供向量化使用
            chunks = documentChunkRepository.selectList(
                    new LambdaQueryWrapper<DocumentChunk>()
                            .eq(DocumentChunk::getDocumentId, documentId)
                            .orderByAsc(DocumentChunk::getChunkIndex)
            );
            syncToEs(chunks);
        }

        log.info("分块完成: documentId={}, 原文{}字符, 切出{}块",
                documentId, text.length(), chunks.size());
        return chunks;
    }

    /**
     * 基于结构化块进行分块。
     * PARAGRAPH 块前置标题路径后走原有段落/句子切分逻辑；
     * TABLE 块转为 Markdown 表格后作为独立 chunk，不参与切分（保证表格完整性）。
     */
    public List<DocumentChunk> chunk(List<ContentBlock> blocks, Long documentId, String documentTitle) {
        if (blocks == null || blocks.isEmpty()) {
            log.warn("结构化块列表为空，跳过分块: documentId={}", documentId);
            return List.of();
        }

        List<DocumentChunk> allChunks = new ArrayList<>();
        int chunkIndex = 0;

        for (ContentBlock block : blocks) {
            if (block.type() == ContentBlock.BlockType.HEADING) {
                continue; // 标题信息已通过 headingPath 注入到后续块中
            }

            String headingPath = block.headingPath();
            String prefixedContent;
            if (!headingPath.isEmpty()) {
                prefixedContent = headingPath + "\n" + block.content();
            } else {
                prefixedContent = block.content();
            }

            if (block.type() == ContentBlock.BlockType.TABLE) {
                // 表格块：不切分，直接作为独立 chunk
                allChunks.add(DocumentChunk.builder()
                        .documentId(documentId)
                        .chunkIndex(chunkIndex)
                        .content(prefixedContent)
                        .metadata(buildMetadata(documentId, documentTitle, chunkIndex, "table", headingPath))
                        .build());
                chunkIndex++;
            } else {
                // 段落块：走原有切分流程
                List<DocumentChunk> subChunks = splitParagraphBlock(prefixedContent, documentId,
                        documentTitle, headingPath, chunkIndex);
                allChunks.addAll(subChunks);
                chunkIndex += subChunks.size();
            }
        }

        if (!allChunks.isEmpty()) {
            documentChunkRepository.insertBatch(allChunks);
            allChunks = documentChunkRepository.selectList(
                    new LambdaQueryWrapper<DocumentChunk>()
                            .eq(DocumentChunk::getDocumentId, documentId)
                            .orderByAsc(DocumentChunk::getChunkIndex)
            );
            syncToEs(allChunks);
        }

        long tableCount = allChunks.stream()
                .filter(c -> {
                    JSONObject meta = JSONUtil.parseObj(c.getMetadata());
                    return "table".equals(meta.getStr("chunk_type"));
                }).count();
        log.info("结构化分块完成: documentId={}, 块数={}, 其中表格块={}", documentId, allChunks.size(), tableCount);
        return allChunks;
    }

    /**
     * 对段落块进行细切分，复用已有的段落/句子切分逻辑
     */
    private List<DocumentChunk> splitParagraphBlock(String text, Long documentId,
                                                     String documentTitle, String headingPath,
                                                     int startIndex) {
        List<DocumentChunk> result = new ArrayList<>();
        List<String> paragraphs = splitByParagraph(text);
        List<String> rawChunks = refineChunks(paragraphs);

        for (int i = 0; i < rawChunks.size(); i++) {
            result.add(DocumentChunk.builder()
                    .documentId(documentId)
                    .chunkIndex(startIndex + i)
                    .content(rawChunks.get(i))
                    .metadata(buildMetadata(documentId, documentTitle, startIndex + i, "text", headingPath))
                    .build());
        }
        return result;
    }

    /**
     * 删除指定文档的所有分块
     */
    public void deleteByDocumentId(Long documentId) {
        int deleted = documentChunkRepository.delete(
            new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getDocumentId, documentId)
        );
        try {
            List<ChunkDocument> esDocs = chunkSearchRepository.findByDocumentId(documentId);
            if (!esDocs.isEmpty()) {
                chunkSearchRepository.deleteAll(esDocs);
            }
        } catch (Exception e) {
            log.warn("ES清理失败，已忽略: documentId={}", documentId, e);
        }
        log.info("已删除分块: documentId={}, 共{}条", documentId, deleted);
    }

    /**
     * 按段落切分
     */
    private List<String> splitByParagraph(String text) {
        List<String> paragraphs = new ArrayList<>();
        for (String part : text.split("\\n\\n")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                paragraphs.add(trimmed);
            }
        }
        return paragraphs;
    }

    /**
     * 段落细切：过长的按句子切，过短的合并
     */
    private List<String> refineChunks(List<String> paragraphs) {
        List<String> result = new ArrayList<>();

        for (String paragraph : paragraphs) {
            if (paragraph.length() <= Constants.CHUNK_MAX_LENGTH) {
                result.add(paragraph);
            } else {
                result.addAll(splitLongParagraph(paragraph));
            }
        }

        return mergeShortChunks(result);
    }

    /**
     * 长段落按句子切分，块间保留 50 字符重叠
     */
    private List<String> splitLongParagraph(String paragraph) {
        List<String> sentences = new ArrayList<>();
        for (String s : SENTENCE_SPLIT.split(paragraph)) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) {
                sentences.add(trimmed);
            }
        }

        List<String> chunks = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();

        for (String sentence : sentences) {
            if (buffer.length() + sentence.length() > Constants.CHUNK_MAX_LENGTH && buffer.length() > 0) {
                chunks.add(buffer.toString());

                // 取末尾 50 字符作为重叠，防止语义卡在边界上
                String tail = buffer.substring(Math.max(0, buffer.length() - 50));
                buffer = new StringBuilder(tail).append(sentence);
            } else {
                buffer.append(sentence);
            }
        }

        if (!buffer.isEmpty()) {
            chunks.add(buffer.toString());
        }

        return chunks;
    }

    /**
     * 将过短的块合并到前一块
     */
    private List<String> mergeShortChunks(List<String> rawChunks) {
        List<String> result = new ArrayList<>();

        for (String chunk : rawChunks) {
            // 只有当合并后不超过最大长度时才合并
            if (chunk.length() < Constants.CHUNK_MIN_LENGTH && !result.isEmpty()) {
                String lastChunk = result.get(result.size() - 1);
                String merged = lastChunk + "\n\n" + chunk;

                // 检查合并后是否超长
                if (merged.length() <= Constants.CHUNK_MAX_LENGTH) {
                    result.set(result.size() - 1, merged);
                } else {
                    // 合并后会超长，单独添加
                    result.add(chunk);
                }
            } else {
                result.add(chunk);
            }
        }

        return result;
    }


    /**
     * 构建分块的元数据 JSON（兼容旧格式）
     */
    private String buildMetadata(Long documentId, String title, int index) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("document_id", documentId);
        meta.put("document_title", title);
        meta.put("chunk_index", index);
        meta.put("chunk_type", "text");
        meta.put("section_path", "");
        return JSONUtil.toJsonStr(meta);
    }

    /**
     * 构建分块的元数据 JSON（带 chunk_type 和 section_path）
     */
    private String buildMetadata(Long documentId, String title, int index,
                                  String chunkType, String sectionPath) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("document_id", documentId);
        meta.put("document_title", title);
        meta.put("chunk_index", index);
        meta.put("chunk_type", chunkType);
        meta.put("section_path", sectionPath != null ? sectionPath : "");
        return JSONUtil.toJsonStr(meta);
    }

    /**
     * 同步分块到 Elasticsearch（失败不影响主流程）
     */
    private void syncToEs(List<DocumentChunk> chunks) {
        try {
            List<ChunkDocument> esDocs = chunks.stream()
                    .map(this::toEsDoc)
                    .collect(Collectors.toList());
            chunkSearchRepository.saveAll(esDocs);
            log.info("ES同步完成: {}条", esDocs.size());
        } catch (Exception e) {
            log.warn("ES同步失败，已忽略: {}", e.getMessage());
        }
    }

    private ChunkDocument toEsDoc(DocumentChunk chunk) {
        JSONObject meta = JSONUtil.parseObj(chunk.getMetadata());
        return ChunkDocument.builder()
                .id(chunk.getId().toString())
                .documentId(chunk.getDocumentId())
                .content(chunk.getContent())
                .documentTitle(meta.getStr("document_title"))
                .chunkIndex(chunk.getChunkIndex())
                .chunkType(meta.getStr("chunk_type"))
                .sectionPath(meta.getStr("section_path"))
                .build();
    }
}
