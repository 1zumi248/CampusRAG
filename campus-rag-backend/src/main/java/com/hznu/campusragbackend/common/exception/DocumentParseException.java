package com.hznu.campusragbackend.common.exception;

/**
 * 文档解析异常 — 返回 422
 */
public class DocumentParseException extends RuntimeException {
    public DocumentParseException(String fileName, Throwable cause) {
        super("文档解析失败: " + fileName, cause);
    }
}
