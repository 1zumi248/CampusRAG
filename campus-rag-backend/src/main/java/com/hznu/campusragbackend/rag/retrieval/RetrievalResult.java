package com.hznu.campusragbackend.rag.retrieval;

import com.hznu.campusragbackend.model.DocumentChunk;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RetrievalResult {
    private DocumentChunk chunk;
    private double similarityScore;  // RRF 融合分
    private double vectorScore;      // 向量原始分数
    private double esScore;          // ES 原始分数
    private int vectorRank;          // 向量排名 (1-based, 0=未上榜)
    private int esRank;              // ES 排名 (1-based, 0=未上榜)

    /** 兼容旧调用:仅 chunk + 融合分,debug 字段填 0 */
    public RetrievalResult(DocumentChunk chunk, double similarityScore) {
        this.chunk = chunk;
        this.similarityScore = similarityScore;
        this.vectorScore = 0;
        this.esScore = 0;
        this.vectorRank = 0;
        this.esRank = 0;
    }
}
