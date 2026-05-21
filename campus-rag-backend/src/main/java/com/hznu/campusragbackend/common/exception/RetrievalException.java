package com.hznu.campusragbackend.common.exception;

/**
 * 检索服务异常 — 返回 502
 */
public class RetrievalException extends RuntimeException {
    public RetrievalException(String detail, Throwable cause) {
        super("检索服务不可用: " + detail, cause);
    }
}
