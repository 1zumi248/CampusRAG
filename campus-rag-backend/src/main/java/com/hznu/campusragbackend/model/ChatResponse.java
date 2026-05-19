package com.hznu.campusragbackend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    private Long conversationId;            // 会话ID
    private String answer;                  // AI生成的答案
    private List<SourceReference> sources;  // 引用来源列表
}
