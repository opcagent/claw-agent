package com.claw.agent.common;

import lombok.Getter;

/**
 * 业务异常（阿里规约：业务逻辑失败抛出业务异常，由全局异常处理器统一转换为 Result）。
 */
@Getter
public class BizException extends RuntimeException {

    /** 错误码 */
    private final int code;

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
