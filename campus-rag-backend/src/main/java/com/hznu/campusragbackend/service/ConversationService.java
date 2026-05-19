package com.hznu.campusragbackend.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hznu.campusragbackend.model.Conversation;
import com.hznu.campusragbackend.model.MessageRecord;
import com.hznu.campusragbackend.model.SourceReference;
import com.hznu.campusragbackend.repository.ConversationRepository;
import com.hznu.campusragbackend.repository.MessageRecordRepository;
import com.hznu.campusragbackend.config.PersistentChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRecordRepository messageRecordRepository;
    private final PersistentChatMemoryStore chatMemoryStore;

    /** 新建会话，返回会话 ID */
    public Conversation createConversation() {
        Conversation conv = Conversation.builder()
                .title("新建会话")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        conversationRepository.insert(conv);
        log.info("新建会话: id={}", conv.getId());
        return conv;
    }

    /** 获取所有会话列表，按更新时间倒序 */
    public List<Conversation> listConversations() {
        return conversationRepository.selectList(
                new LambdaQueryWrapper<Conversation>()
                        .orderByDesc(Conversation::getUpdatedAt)
        );
    }

    /** 获取某个会话的历史消息 */
    public List<MessageRecord> getMessages(Long conversationId) {
        return messageRecordRepository.selectList(
                new LambdaQueryWrapper<MessageRecord>()
                        .eq(MessageRecord::getConversationId, conversationId)
                        .orderByAsc(MessageRecord::getCreatedAt)
        );
    }

    /** 保存一轮问答 */
    public MessageRecord saveMessage(Long conversationId, String question, String answer,
                                      List<SourceReference> sources) {
        MessageRecord record = MessageRecord.builder()
                .conversationId(conversationId)
                .question(question)
                .answer(answer)
                .sources(sources != null ? JSONUtil.toJsonStr(sources) : null)
                .createdAt(LocalDateTime.now())
                .build();
        messageRecordRepository.insert(record);

        // 始终更新 updated_at
        Conversation update = new Conversation();
        update.setId(conversationId);
        update.setUpdatedAt(LocalDateTime.now());
        conversationRepository.updateById(update);

        // 仅当标题还是默认值时才设置标题（条件 UPDATE，无需先 SELECT）
        if (question != null) {
            String newTitle = question.length() > 30 ? question.substring(0, 30) + "…" : question;
            conversationRepository.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Conversation>()
                            .eq(Conversation::getId, conversationId)
                            .eq(Conversation::getTitle, "新建会话")
                            .set(Conversation::getTitle, newTitle));
        }

        return record;
    }

    /** 删除会话及其关联消息，同步清理 ChatMemory */
    public void deleteConversation(Long conversationId) {
        conversationRepository.deleteById(conversationId);
        chatMemoryStore.deleteMessages(conversationId);
        log.info("删除会话: id={}", conversationId);
    }
}
