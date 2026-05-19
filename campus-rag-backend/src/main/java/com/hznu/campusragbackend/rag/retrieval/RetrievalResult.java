package com.hznu.campusragbackend.rag.retrieval;

import com.hznu.campusragbackend.model.DocumentChunk;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RetrievalResult {
    private DocumentChunk chunk;
    private double similarityScore;
}
