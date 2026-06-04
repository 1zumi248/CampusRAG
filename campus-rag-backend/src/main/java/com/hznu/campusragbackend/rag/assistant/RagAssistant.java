package com.hznu.campusragbackend.rag.assistant;

import com.hznu.campusragbackend.config.PromptTemplates;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface RagAssistant {

    @SystemMessage(PromptTemplates.RAG_SYSTEM_PROMPT)
    @UserMessage("{{question}}")
    String answer(@V("question") String question, @MemoryId Long conversationId);

    @SystemMessage(PromptTemplates.RAG_SYSTEM_PROMPT)
    @UserMessage("{{question}}")
    TokenStream stream(@V("question") String question, @MemoryId Long conversationId);
}