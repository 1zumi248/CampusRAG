package com.hznu.campusragbackend.rag.parser;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedDocumentResult {
    
    /**
     * 提取的文本内容
     */
    private String content;
    
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
