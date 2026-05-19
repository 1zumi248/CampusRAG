package com.hznu.campusragbackend.model;

import com.baomidou.mybatisplus.annotation.*;
import com.hznu.campusragbackend.config.JsonbTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("messages")
public class MessageRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("conversation_id")
    private Long conversationId;

    @TableField("question")
    private String question;

    @TableField("answer")
    private String answer;

    @TableField(value = "sources", typeHandler = JsonbTypeHandler.class)
    private String sources;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
