package com.sankai.agent.api;

import com.sankai.agent.exception.ExtractionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 全局异常处理器，统一处理 API 请求中的各种异常。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理提取过程中抛出的 ExtractionException 异常。
     *
     * @param ex 捕获到的提取异常
     * @return 包含错误信息的响应实体
     */
    @ExceptionHandler(ExtractionException.class)
    public ResponseEntity<Map<String, String>> handleExtraction(ExtractionException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("error", ex.getMessage()));
    }

    /**
     * 处理参数校验失败抛出的 MethodArgumentNotValidException 异常。
     *
     * @param ex 捕获到的参数校验异常
     * @return 包含固定错误信息的响应实体
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("error", "invalid request"));
    }
}
