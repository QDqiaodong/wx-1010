package com.icepark.exception;

/**
 * 业务规则校验失败（HTTP 400），message 直接面向现场工作人员，
 * 需要说明具体是哪一条规则不满足。
 */
public class BusinessValidationException extends RuntimeException {
    public BusinessValidationException(String message) {
        super(message);
    }
}
