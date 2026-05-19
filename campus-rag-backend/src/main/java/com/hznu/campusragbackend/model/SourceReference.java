package com.hznu.campusragbackend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceReference {
    private Long documentId;        // 文档ID
    private String documentTitle;   // 文档标题
    private String chunkContent;    // 片段内容
    private Integer chunkIndex;     // 片段索引
    private Double similarityScore; // 相似度分数
}
