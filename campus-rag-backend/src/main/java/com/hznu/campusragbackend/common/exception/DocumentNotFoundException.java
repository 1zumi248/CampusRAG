package com.hznu.campusragbackend.common.exception;

/**
 * 文档不存在异常 — 返回 404
 */
public class DocumentNotFoundException extends RuntimeException {
    public DocumentNotFoundException(Long documentId) {
        super("文档不存在: id=" + documentId);
    }
}
