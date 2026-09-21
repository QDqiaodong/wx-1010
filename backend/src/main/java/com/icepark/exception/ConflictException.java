package com.icepark.exception;

/**
 * 业务冲突（HTTP 409）：典型场景为同一件器材在并发下已被他人领用、重复归还等。
 * 与普通参数/状态校验（400）区分，便于前端明确提示“已被领用”。
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
