package com.hznu.campusragbackend.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hznu.campusragbackend.model.DocumentChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;

import java.util.List;

@Mapper
public interface DocumentChunkRepository extends BaseMapper<DocumentChunk> {

    @Insert({
        "<script>",
        "INSERT INTO document_chunks (document_id, chunk_index, content, metadata) VALUES",
        "<foreach collection='chunks' item='c' separator=','>",
        "(#{c.documentId}, #{c.chunkIndex}, #{c.content}, #{c.metadata}::jsonb)",
        "</foreach>",
        "</script>"
    })
    void insertBatch(@Param("chunks") List<DocumentChunk> chunks);
}
