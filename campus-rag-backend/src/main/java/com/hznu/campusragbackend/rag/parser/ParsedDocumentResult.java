package com.hznu.campusragbackend.rag.parser;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedDocumentResult {

    /**
     * 提取的文本内容（HTML 格式，保留文档结构）
     */
    private String content;

    /**
     * 纯文本内容（从 HTML 中提取），用于降级分块和空内容校验
     */
    private String plainText;

    /**
     * 结构化内容块（标题路径、表格、段落），供分块使用。
     * 纯文本文档或 HTML 解析失败时为空列表，调用方降级为纯文本分块。
     */
    @Builder.Default
    private List<ContentBlock> contentBlocks = new ArrayList<>();

    /**
     * 文件类型（MIME Type）
     */
    private String fileType;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;
}
