package com.hznu.campusragbackend.config;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于 PostgreSQL 的 ChatMemoryStore 实现，会话记忆持久化到 chat_memory 表。
 * 使用 LangChain4j 官方序列化工具确保兼容性。
 */
@Slf4j
@Component
public class PersistentChatMemoryStore implements ChatMemoryStore {

    private final JdbcTemplate jdbcTemplate;

    private static final String UPSERT_SQL = """
            INSERT INTO chat_memory (memory_id, messages) VALUES (?, ?::jsonb)
            ON CONFLICT (memory_id) DO UPDATE SET messages = EXCLUDED.messages
            """;

    private static final String SELECT_SQL = "SELECT messages FROM chat_memory WHERE memory_id = ?";

    private static final String DELETE_SQL = "DELETE FROM chat_memory WHERE memory_id = ?";

    public PersistentChatMemoryStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        List<String> rows = jdbcTemplate.queryForList(SELECT_SQL, String.class, memoryId.toString());
        if (rows.isEmpty()) {
            log.debug("未找到会话历史: memoryId={}", memoryId);
            return List.of();
        }
        try {
            String json = rows.get(0);
            List<ChatMessage> messages = ChatMessageDeserializer.messagesFromJson(json);
            log.debug("加载会话历史成功: memoryId={}, 消息数={}", memoryId, messages.size());
            return messages;
        } catch (Exception e) {
            log.error("反序列化 ChatMemory 失败: memoryId={}", memoryId, e);
            // 删除损坏的数据
            jdbcTemplate.update(DELETE_SQL, memoryId.toString());
            return List.of();
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            jdbcTemplate.update(DELETE_SQL, memoryId.toString());
            log.debug("清空会话历史: memoryId={}", memoryId);
            return;
        }
        try {
            String json = ChatMessageSerializer.messagesToJson(messages);
            jdbcTemplate.update(UPSERT_SQL, memoryId.toString(), json);
            log.debug("保存会话历史成功: memoryId={}, 消息数={}", memoryId, messages.size());
        } catch (Exception e) {
            log.error("序列化 ChatMemory 失败: memoryId={}, 消息数={}", memoryId, messages.size(), e);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        jdbcTemplate.update(DELETE_SQL, memoryId.toString());
        log.debug("删除会话历史: memoryId={}", memoryId);
    }
}
