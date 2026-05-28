package com.hznu.campusragbackend.rag.retrieval;

import dev.langchain4j.data.embedding.Embedding;

import java.util.Map;

public interface RetrievalStrategy {
    Map<Long, Double> search(String query, Embedding queryEmbedding, int maxResults);
}
