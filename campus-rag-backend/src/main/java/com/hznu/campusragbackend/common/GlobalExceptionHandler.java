package com.hznu.campusragbackend.common;

import com.hznu.campusragbackend.common.exception.DocumentNotFoundException;
import com.hznu.campusragbackend.common.exception.DocumentParseException;
import com.hznu.campusragbackend.common.exception.RetrievalException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DocumentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleDocumentNotFound(DocumentNotFoundException e) {
        log.warn("文档不存在: {}", e.getMessage());
        return Result.error(404, e.getMessage());
    }

    @ExceptionHandler(DocumentParseException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public Result<Void> handleDocumentParse(DocumentParseException e) {
        log.error("文档解析失败", e);
        return Result.error(422, e.getMessage());
    }

    @ExceptionHandler(RetrievalException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Result<Void> handleRetrieval(RetrievalException e) {
        log.error("检索服务异常", e);
        return Result.error(502, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String fields = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("请求参数校验失败: {}", fields);
        return Result.error(400, "参数校验失败: " + fields);
    }

    @ExceptionHandler(ResourceAccessException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Result<Void> handleResourceAccess(ResourceAccessException e) {
        log.error("外部 AI 服务连接失败", e);
        return Result.error(503, "AI 服务连接失败，请检查网络或代理设置后重试");
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleRuntimeException(RuntimeException e) {
        log.error("系统异常", e);
        return Result.error(500, "服务器内部错误");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("未知异常", e);
        return Result.error(500, "服务器内部错误");
    }
}
