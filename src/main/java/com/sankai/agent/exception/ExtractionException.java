package com.sankai.agent.exception;

/**
 * 提取过程中发生的运行时异常，主要用于标识由于重试次数耗尽或解析错误导致的任务失败。
 */
public class ExtractionException extends RuntimeException {
    
    /**
     * 构造一个新的提取异常。
     *
     * @param message 异常详细描述
     * @param cause   导致此异常的根本原因
     */
    public ExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
