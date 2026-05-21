package com.hznu.campusragbackend.rag.parser;

import com.hznu.campusragbackend.common.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToHTMLContentHandler;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentParserService {

    private final Tika tika = new Tika();
    private static final AutoDetectParser PARSER = new AutoDetectParser();
    private final HtmlParserService htmlParserService;
    /**
     * 危险文件类型黑名单（MIME 前缀匹配）
     * Tika 能解析什么就放什么，只拦截可执行文件/脚本
     */
    private static final Set<String> BLOCKED_MIME_PREFIXES = Set.of(
            "application/x-msdownload",     // .exe
            "application/x-msdos-program",  // .com
            "application/x-dosexec",        // PE 可执行文件
            "application/x-msi",            // .msi 安装包
            "application/x-sh",             // shell 脚本
            "application/x-bat",            // batch 脚本
            "application/x-powershell",     // PowerShell 脚本
            "application/x-perl",           // Perl 脚本
            "application/x-python",         // Python 脚本 (.py)
            "application/java-archive"      // .jar 可能带恶意代码
    );

    public ParsedDocumentResult parse(MultipartFile file) {
        try {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.isBlank()) {
                originalFilename = "unnamed_document";
                log.warn("文件名为空，使用默认名称: {}", originalFilename);
            }

            long fileSize = file.getSize();

            log.info("开始解析文件: {}, 大小: {} bytes", originalFilename, fileSize);

            String fileType = tika.detect(originalFilename);
            log.debug("检测到文件类型: {}", fileType);
            for (String blocked : BLOCKED_MIME_PREFIXES){
                if ( fileType!=null && fileType.startsWith(blocked)){
                    throw new IllegalArgumentException("不支持的文件类型: " + fileType);
                }
            }

            ParsedHtmlHolder parsedHtml = extractHtml(file);
            String htmlContent = parsedHtml.html();
            String plainText = parsedHtml.plainText();

            // HTML 过大防护：避免超大文档导致后续解析 OOM
            if (htmlContent.length() > Constants.DOC_PARSE_MAX_CHARS * 2) {
                throw new IllegalArgumentException(
                        "文档内容过大（" + htmlContent.length() + " 字符），超过上限 " + Constants.DOC_PARSE_MAX_CHARS * 2);
            }

            //空文本检查（Tika 无法识别的格式/损坏文件/图片等）
            if (plainText.isBlank()) {
                throw new IllegalArgumentException("无法从文件中提取文本内容，文件可能为图片、扫描件或不受支持的格式");
            }

            // 提取结构化块（标题层级 + 表格 + 段落）
            List<ContentBlock> contentBlocks = htmlParserService.parse(htmlContent);

            log.info("文件解析成功: {}, 提取文本长度: {} 字符, 结构化块: {} 个",
                    originalFilename, plainText.length(), contentBlocks.size());

            return ParsedDocumentResult.builder()
                    .content(htmlContent)
                    .plainText(plainText)
                    .contentBlocks(contentBlocks)
                    .fileType(fileType)
                    .fileName(originalFilename)
                    .fileSize(fileSize)
                    .build();

        } catch (Exception e) {
            log.error("文档解析失败: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("文档解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 使用 ToHTMLContentHandler 提取文件的 HTML 内容。
     * HTML 保留标题层级、表格等结构信息，纯文本由 Jsoup 从 HTML 中提取。
     */
    private ParsedHtmlHolder extractHtml(MultipartFile file) throws Exception {
        ToHTMLContentHandler handler = new ToHTMLContentHandler();
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();

        try (InputStream inputStream = file.getInputStream()) {
            PARSER.parse(inputStream, handler, metadata, context);
        }

        String html = handler.toString();
        // 用 Jsoup 提取纯文本用于空内容校验（无需二次 parse）
        String plainText = org.jsoup.Jsoup.parse(html).body().text();
        return new ParsedHtmlHolder(html, plainText);
    }

    private record ParsedHtmlHolder(String html, String plainText) {}
}
