package com.hznu.campusragbackend.model;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import java.util.Map;

public record ChunkMetadata(
        Long documentId,
        String documentTitle,
        int chunkIndex,
        String chunkType,
        String sectionPath
) {
    public static ChunkMetadata fromJson(String json) {
        JSONObject obj = JSONUtil.parseObj(json);
        return new ChunkMetadata(
                obj.getLong("document_id"),
                obj.getStr("document_title", "未知文档"),
                obj.getInt("chunk_index", 0),
                obj.getStr("chunk_type", "text"),
                obj.getStr("section_path", "")
        );
    }

    public String toJson() {
        return JSONUtil.toJsonStr(Map.of(
                "document_id", documentId != null ? documentId : 0,
                "document_title", documentTitle != null ? documentTitle : "",
                "chunk_index", chunkIndex,
                "chunk_type", chunkType != null ? chunkType : "text",
                "section_path", sectionPath != null ? sectionPath : ""
        ));
    }
}
