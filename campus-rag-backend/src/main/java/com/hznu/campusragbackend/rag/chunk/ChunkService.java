package com.hznu.campusragbackend.rag.chunk;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hznu.campusragbackend.common.Constants;
import com.hznu.campusragbackend.model.DocumentChunk;
import com.hznu.campusragbackend.repository.DocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkService {

    private final DocumentChunkRepository documentChunkRepository;

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

        // 第三步：持久化到 document_chunks 表
        List<DocumentChunk> chunks = new ArrayList<>();
        for (int i = 0; i < rawChunks.size(); i++) {
            DocumentChunk chunk = DocumentChunk.builder()
                    .documentId(documentId)
                    .chunkIndex(i)
                    .content(rawChunks.get(i))
                    .metadata(buildMetadata(documentId, documentTitle, i))
                    .build();

            documentChunkRepository.insert(chunk);
            chunks.add(chunk);
        }

        log.info("分块完成: documentId={}, 原文{}字符, 切出{}块",
                documentId, text.length(), chunks.size());
        return chunks;
    }

    /**
     * 删除指定文档的所有分块
     */
    public void deleteByDocumentId(Long documentId) {
        int deleted = documentChunkRepository.delete(
            new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getDocumentId, documentId)
        );
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
     * 构建分块的元数据 JSON
     */
    private String buildMetadata(Long documentId, String title, int index) {
        Map<String, Object> meta = Map.of(
                "document_id", documentId,
                "document_title", title,
                "chunk_index", index
        );
        return JSONUtil.toJsonStr(meta);
    }
}
