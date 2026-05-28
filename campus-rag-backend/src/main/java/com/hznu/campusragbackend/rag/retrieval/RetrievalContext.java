package com.hznu.campusragbackend.rag.retrieval;

import java.util.List;

public record RetrievalContext(String formattedText, List<RetrievalResult> results) {
}
