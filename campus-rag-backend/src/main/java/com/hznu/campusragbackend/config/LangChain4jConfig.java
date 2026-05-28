package com.hznu.campusragbackend.config;

import com.hznu.campusragbackend.agent.tools.ClassroomTool;
import com.hznu.campusragbackend.agent.tools.CurrentTimeTool;
import com.hznu.campusragbackend.agent.tools.LibrarySeatTool;
import com.hznu.campusragbackend.agent.tools.RagRetrievalTool;
import com.hznu.campusragbackend.agent.tools.ScheduleTool;
import com.hznu.campusragbackend.agent.tools.WeatherTool;
import com.hznu.campusragbackend.rag.assistant.RagAssistant;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class LangChain4jConfig {

    @Value("${pgvector.host}")
    private String host;

    @Value("${pgvector.port}")
    private Integer port;

    @Value("${pgvector.database}")
    private String database;

    @Value("${pgvector.user}")
    private String user;

    @Value("${pgvector.password}")
    private String password;

    @Value("${pgvector.table}")
    private String table;

    @Value("${pgvector.dimension}")
    private Integer dimension;

    @Value("${langchain4j.open-ai.api-key}")
    private String apiKey;

    @Value("${langchain4j.open-ai.base-url}")
    private String baseUrl;

    @Value("${langchain4j.open-ai.chat-model.model-name}")
    private String chatModelName;

    @Value("${langchain4j.open-ai.chat-model.temperature:0.7}")
    private Double temperature;

    @Value("${langchain4j.open-ai.embedding-model.model-name}")
    private String embeddingModelName;

    /** 用具体返回类型声明，auto-config 的 @ConditionalOnMissingBean 会检测到并跳过 */
    @Bean
    public OpenAiChatModel chatModel() {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(chatModelName)
                .temperature(temperature)
                .timeout(Duration.ofSeconds(30))
                .build();
    }

    @Bean
    public OpenAiStreamingChatModel streamingChatModel() {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(chatModelName)
                .temperature(temperature)
                .timeout(Duration.ofSeconds(30))
                .build();
    }

    @Bean
    public OpenAiEmbeddingModel embeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(embeddingModelName)
                .timeout(Duration.ofSeconds(30))
                .build();
    }

    @Bean
    public PgVectorEmbeddingStore embeddingStore() {
        return PgVectorEmbeddingStore.builder()
                .host(host)
                .port(port)
                .database(database)
                .user(user)
                .password(password)
                .table(table)
                .dimension(dimension)
                .createTable(true)
                .build();
    }

    @Bean
    public ChatMemoryProvider chatMemoryProvider(ChatMemoryStore store) {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(6)
                .chatMemoryStore(store)
                .build();
    }

    @Bean
    public RagAssistant ragAssistant(OpenAiChatModel chatModel,
                                     OpenAiStreamingChatModel streamingChatModel,
                                     ChatMemoryProvider chatMemoryProvider,
                                     RagRetrievalTool retrievalTool,
                                     WeatherTool weatherTool,
                                     ScheduleTool scheduleTool,
                                     LibrarySeatTool librarySeatTool,
                                     ClassroomTool classroomTool,
                                     CurrentTimeTool currentTimeTool) {
        return AiServices.builder(RagAssistant.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .tools(retrievalTool, weatherTool, scheduleTool, librarySeatTool, classroomTool, currentTimeTool)
                .build();
    }
}
