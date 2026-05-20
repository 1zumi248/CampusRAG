package com.hznu.campusragbackend.rag.parser;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 解析 Tika 输出的 HTML，提取文档结构：标题层级、段落、表格。
 * 每个非标题块会关联当前所处的标题路径，解决"标题-内容分离"问题。
 */
@Slf4j
@Component
public class HtmlParserService {

    /**
     * 解析 HTML 字符串，产出有序的结构化块列表
     */
    public List<ContentBlock> parse(String html) {
        List<ContentBlock> blocks = new ArrayList<>();
        if (html == null || html.isBlank()) {
            return blocks;
        }

        Document doc = Jsoup.parse(html);
        Element body = doc.body();
        if (body == null) {
            return blocks;
        }

        List<String> headingStack = new ArrayList<>();
        StringBuilder textBuffer = new StringBuilder();
        int order = 0;

        for (Node child : body.childNodes()) {
            if (child instanceof Element el) {
                String tag = el.normalName();

                if (tag.matches("h[1-6]")) {
                    // 标题节点：先提交缓冲区中的文本，再处理标题
                    flushTextBuffer(textBuffer, headingStack, order++, blocks);
                    int level = Integer.parseInt(tag.substring(1));
                    String headingText = el.wholeText().trim();
                    if (!headingText.isEmpty()) {
                        truncateStack(headingStack, level - 1);
                        headingStack.add(headingText);
                        blocks.add(ContentBlock.heading(headingText, stackToPath(headingStack), level, order++));
                    }
                } else if (tag.equals("table")) {
                    // 表格节点：先提交缓冲区文本，再处理表格
                    flushTextBuffer(textBuffer, headingStack, order++, blocks);
                    String markdown = convertTableToMarkdown(el);
                    if (!markdown.isEmpty()) {
                        blocks.add(ContentBlock.table(markdown, stackToPath(headingStack), order++));
                    }
                } else {
                    // 段落/div/其他容器：提取内部文本
                    String text = el.wholeText();
                    if (text != null && !text.isBlank()) {
                        if (!textBuffer.isEmpty()) {
                            textBuffer.append("\n\n");
                        }
                        textBuffer.append(text.trim());
                    }
                }
            } else if (child instanceof TextNode tn) {
                String text = tn.getWholeText().trim();
                if (!text.isEmpty()) {
                    if (!textBuffer.isEmpty()) {
                        textBuffer.append("\n\n");
                    }
                    textBuffer.append(text);
                }
            }
        }

        // 提交末尾缓冲区
        flushTextBuffer(textBuffer, headingStack, order, blocks);

        log.info("HTML 解析完成: 提取 {} 个结构化块 (标题={}, 表格={}, 段落={})",
                blocks.size(),
                blocks.stream().filter(b -> b.type() == ContentBlock.BlockType.HEADING).count(),
                blocks.stream().filter(b -> b.type() == ContentBlock.BlockType.TABLE).count(),
                blocks.stream().filter(b -> b.type() == ContentBlock.BlockType.PARAGRAPH).count());

        return blocks;
    }

    // ========== 内部方法 ==========

    /** 将文本缓冲区提交为一个 PARAGRAPH 块 */
    private void flushTextBuffer(StringBuilder buffer, List<String> headingStack,
                                  int order, List<ContentBlock> blocks) {
        if (buffer.isEmpty()) {
            return;
        }
        String text = buffer.toString().trim();
        buffer.setLength(0);
        if (!text.isEmpty()) {
            blocks.add(ContentBlock.paragraph(text, stackToPath(headingStack), order));
        }
    }

    /** 截断标题栈到指定深度 */
    private void truncateStack(List<String> stack, int maxDepth) {
        while (stack.size() > maxDepth) {
            stack.remove(stack.size() - 1);
        }
    }

    /** 标题栈转为路径字符串 */
    private String stackToPath(List<String> stack) {
        if (stack.isEmpty()) {
            return "";
        }
        return String.join(" > ", stack);
    }

    /**
     * 将 HTML table 转为 Markdown 表格格式
     */
    private String convertTableToMarkdown(Element table) {
        Elements rows = table.select("tr");
        if (rows.isEmpty()) {
            return "";
        }

        List<List<String>> tableData = new ArrayList<>();
        int maxCols = 0;

        for (Element row : rows) {
            List<String> cells = new ArrayList<>();
            // 同时取 th 和 td（处理表头和表体）
            for (Element cell : row.select("th, td")) {
                String cellText = cell.wholeText().trim();
                // 清理换行符，保持单元格内容在单行
                cellText = cellText.replace('\n', ' ').replaceAll("\\s+", " ");
                cells.add(cellText);
            }
            if (!cells.isEmpty()) {
                tableData.add(cells);
                if (cells.size() > maxCols) {
                    maxCols = cells.size();
                }
            }
        }

        if (tableData.isEmpty()) {
            return "";
        }

        // 补齐列数不一致的行
        for (List<String> row : tableData) {
            while (row.size() < maxCols) {
                row.add("");
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tableData.size(); i++) {
            List<String> row = tableData.get(i);
            sb.append("| ");
            sb.append(String.join(" | ", row));
            sb.append(" |\n");

            // 第一行后添加分隔线
            if (i == 0) {
                sb.append("| ");
                for (int j = 0; j < maxCols; j++) {
                    if (j > 0) sb.append(" | ");
                    sb.append("---");
                }
                sb.append(" |\n");
            }
        }

        return sb.toString().trim();
    }
}
