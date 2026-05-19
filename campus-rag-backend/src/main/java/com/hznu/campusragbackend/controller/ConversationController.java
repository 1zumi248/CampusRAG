package com.hznu.campusragbackend.controller;

import com.hznu.campusragbackend.common.Result;
import com.hznu.campusragbackend.model.Conversation;
import com.hznu.campusragbackend.model.MessageRecord;
import com.hznu.campusragbackend.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    /** 新建会话 */
    @PostMapping
    public Result<Conversation> createConversation() {
        return Result.ok(conversationService.createConversation());
    }

    /** 获取会话列表 */
    @GetMapping
    public Result<List<Conversation>> listConversations() {
        return Result.ok(conversationService.listConversations());
    }

    /** 获取某个会话的历史消息 */
    @GetMapping("/{id}/messages")
    public Result<List<MessageRecord>> getMessages(@PathVariable Long id) {
        return Result.ok(conversationService.getMessages(id));
    }

    /** 删除会话 */
    @DeleteMapping("/{id}")
    public Result<Void> deleteConversation(@PathVariable Long id) {
        conversationService.deleteConversation(id);
        return Result.ok();
    }
}
