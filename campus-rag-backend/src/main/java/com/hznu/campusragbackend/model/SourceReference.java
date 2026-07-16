package com.hznu.campusragbackend.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SourceReference {
    private Long documentId;        // 文档ID
    private String documentTitle;   // 文档标题
    private String chunkContent;    // 片段内容
    private Integer chunkIndex;     // 片段索引
    private Double similarityScore; // RRF 融合分数

    // 调试字段(仅流式 SSE 发送,不持久化到数据库)
    private Double vectorScore;     // 向量原始分数
    private Double esScore;         // ES 原始分数
    private Integer vectorRank;     // 向量排名 (1-based)
    private Integer esRank;         // ES 排名 (1-based)
}
