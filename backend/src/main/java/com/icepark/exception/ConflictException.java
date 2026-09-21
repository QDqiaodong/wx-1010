package com.icepark.exception;

/**
 * 业务冲突类失败（HTTP 409）：典型场景是同一件器材并发领用，
 * 后到的一方需要明确收到"已被领用"而不是双方都成功。
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
