package com.hznu.campusragbackend.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("document_chunks")
public class DocumentChunk {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    @TableField("document_id")
    private Long documentId;
    
    @TableField("chunk_index")
    private Integer chunkIndex;
    
    @TableField("content")
    private String content;
    
    @TableField(value = "metadata", typeHandler = com.hznu.campusragbackend.config.JsonbTypeHandler.class)
    private String metadata;
    
    @TableField("created_at")
    private LocalDateTime createdAt;
}
