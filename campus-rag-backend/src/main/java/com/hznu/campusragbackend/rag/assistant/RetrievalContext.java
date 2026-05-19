package com.hznu.campusragbackend.rag.assistant;

import com.hznu.campusragbackend.rag.retrieval.RetrievalResult;

import java.util.List;

public record RetrievalContext(String formattedText, List<RetrievalResult> results) {
}
