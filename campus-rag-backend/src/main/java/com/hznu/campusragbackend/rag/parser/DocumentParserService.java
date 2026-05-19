package com.hznu.campusragbackend.rag.parser;

import com.hznu.campusragbackend.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Set;

@Slf4j
@Service
public class DocumentParserService {

    private final Tika tika = new Tika();
    private static final AutoDetectParser PARSER = new AutoDetectParser();
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

            String content = extractText(file);
            //空文本检查（Tika 无法识别的格式/损坏文件/图片等）
            if (content.isBlank()) {
                throw new IllegalArgumentException("无法从文件中提取文本内容，文件可能为图片、扫描件或不受支持的格式");
            }

            log.info("文件解析成功: {}, 提取文本长度: {} 字符", originalFilename, content.length());

            return ParsedDocumentResult.builder()
                    .content(content)
                    .fileType(fileType)
                    .fileName(originalFilename)
                    .fileSize(fileSize)
                    .build();

        } catch (Exception e) {
            log.error("文档解析失败: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("文档解析失败: " + e.getMessage(), e);
        }
    }

    private String extractText(MultipartFile file) throws Exception {
        BodyContentHandler handler = new BodyContentHandler(Constants.DOC_PARSE_MAX_CHARS);
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();

        try (InputStream inputStream = file.getInputStream()) {
            PARSER.parse(inputStream, handler, metadata, context);
            return handler.toString();
        }
    }
}
