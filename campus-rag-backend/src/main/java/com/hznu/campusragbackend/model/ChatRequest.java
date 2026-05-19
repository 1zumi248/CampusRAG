package com.hznu.campusragbackend.model;

import lombok.Data;

@Data
public class ChatRequest {
    private String question;       // 用户问题
    private Long conversationId;   // 会话ID，null 表示新建会话
}
