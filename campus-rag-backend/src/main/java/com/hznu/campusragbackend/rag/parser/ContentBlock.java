package com.hznu.campusragbackend.rag.parser;

/**
 * 文档结构化块：按类型（标题/段落/表格）组织，承载当前标题路径
 */
public record ContentBlock(
        BlockType type,
        String content,       // 文本或 Markdown 表格
        String headingPath,   // 当前标题路径，如 "第六章 > 奖学金评定"
        int headingLevel,     // 仅 HEADING 类型有效 (1-6)
        int order             // 文档中出现顺序
) {

    public enum BlockType {
        HEADING,
        PARAGRAPH,
        TABLE
    }

    public static ContentBlock heading(String content, String headingPath, int level, int order) {
        return new ContentBlock(BlockType.HEADING, content, headingPath, level, order);
    }

    public static ContentBlock paragraph(String content, String headingPath, int order) {
        return new ContentBlock(BlockType.PARAGRAPH, content, headingPath, 0, order);
    }

    public static ContentBlock table(String markdownTable, String headingPath, int order) {
        return new ContentBlock(BlockType.TABLE, markdownTable, headingPath, 0, order);
    }
}
