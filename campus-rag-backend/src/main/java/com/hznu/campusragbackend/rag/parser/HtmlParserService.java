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
import java.util.regex.Pattern;

/**
 * 解析 Tika 输出的 HTML，提取文档结构：标题层级、段落、表格、列表。
 * 每个非标题块会关联当前所处的标题路径，解决"标题-内容分离"问题。
 */
@Slf4j
@Component
public class HtmlParserService {

    private static final Pattern HEADING_TAG = Pattern.compile("h[1-6]");
    private static final Pattern LIST_TAG = Pattern.compile("ul|ol");
    private static final Pattern CODE_TAG = Pattern.compile("pre|code");

    /**
     * 解析 HTML 字符串，产出有序的结构化块列表
     */
    public List<ContentBlock> parse(String html) {
        List<ContentBlock> blocks = new ArrayList<>();
        if (html == null || html.isBlank()) {
            return blocks;
        }

        Document doc = Jsoup.parse(html);
        // body 不存在时回退到 document 根元素（兼容 Tika PDF 等无 body 的输出）
        Element root = doc.body() != null ? doc.body() : doc;

        List<String> headingStack = new ArrayList<>();
        StringBuilder textBuffer = new StringBuilder();
        int order = 0;

        for (Node child : root.childNodes()) {
            if (child instanceof Element el) {
                String tag = el.normalName();

                if (HEADING_TAG.matcher(tag).matches()) {
                    // 标题节点：先提交缓冲区中的文本，再处理标题
                    if (flushTextBuffer(textBuffer, headingStack, blocks, order)) {
                        order++;
                    }
                    int level = Integer.parseInt(tag.substring(1));
                    String headingText = el.wholeText().trim();
                    if (!headingText.isEmpty()) {
                        truncateStack(headingStack, level - 1);
                        headingStack.add(headingText);
                        blocks.add(ContentBlock.heading(headingText, stackToPath(headingStack), level, order++));
                    }
                } else if (tag.equals("table")) {
                    if (flushTextBuffer(textBuffer, headingStack, blocks, order)) {
                        order++;
                    }
                    String markdown = convertTableToMarkdown(el);
                    if (!markdown.isEmpty()) {
                        blocks.add(ContentBlock.table(markdown, stackToPath(headingStack), order++));
                    }
                } else if (LIST_TAG.matcher(tag).matches()) {
                    // 列表：提取各列表项文本，用换行连接，保持结构
                    if (flushTextBuffer(textBuffer, headingStack, blocks, order)) {
                        order++;
                    }
                    String listText = extractListText(el);
                    if (!listText.isEmpty()) {
                        textBuffer.append(listText);
                    }
                } else if (CODE_TAG.matcher(tag).matches()) {
                    // 代码块：保留原始格式，用反引号包裹
                    if (flushTextBuffer(textBuffer, headingStack, blocks, order)) {
                        order++;
                    }
                    String codeText = el.wholeText().trim();
                    if (!codeText.isEmpty()) {
                        textBuffer.append("```\n").append(codeText).append("\n```");
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
        flushTextBuffer(textBuffer, headingStack, blocks, order);

        log.info("HTML 解析完成: 提取 {} 个结构化块 (标题={}, 表格={}, 段落={})",
                blocks.size(),
                blocks.stream().filter(b -> b.type() == ContentBlock.BlockType.HEADING).count(),
                blocks.stream().filter(b -> b.type() == ContentBlock.BlockType.TABLE).count(),
                blocks.stream().filter(b -> b.type() == ContentBlock.BlockType.PARAGRAPH).count());

        return blocks;
    }

    // ========== 内部方法 ==========

    /** 将文本缓冲区提交为一个 PARAGRAPH 块，返回是否实际刷新了内容 */
    private boolean flushTextBuffer(StringBuilder buffer, List<String> headingStack,
                                     List<ContentBlock> blocks, int order) {
        if (buffer.isEmpty()) {
            return false;
        }
        String text = buffer.toString().trim();
        buffer.setLength(0);
        if (!text.isEmpty()) {
            blocks.add(ContentBlock.paragraph(text, stackToPath(headingStack), order));
            return true;
        }
        return false;
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
     * 将 HTML table 转为 Markdown 表格格式。
     * 使用 > 限定直接子行，避免嵌套表格干扰。
     */
    private String convertTableToMarkdown(Element table) {
        // 只取直接子行（thead/tbody 下的 tr 或直接 tr），排除嵌套表格的行
        Elements rows = table.select("> thead > tr, > tbody > tr, > tr");
        if (rows.isEmpty()) {
            return "";
        }

        List<List<String>> tableData = new ArrayList<>();
        int maxCols = 0;

        for (Element row : rows) {
            List<String> cells = new ArrayList<>();
            for (Element cell : row.select("> th, > td")) {
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

    /** 提取列表文本，每项加前缀保持可读性 */
    private String extractListText(Element list) {
        Elements items = list.select("> li");
        if (items.isEmpty()) {
            return list.wholeText().trim();
        }
        StringBuilder sb = new StringBuilder();
        for (Element item : items) {
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append("- ").append(item.wholeText().trim());
        }
        return sb.toString();
    }
}
