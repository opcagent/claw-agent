package com.claw.agent.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器（阿里规约：统一异常出口，避免异常堆栈直接暴露给前端）。
 * <p>
 * 处理顺序：业务异常 → 参数校验异常 → 兜底异常。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常：转换为对应错误码的 Result 返回。
     *
     * @param e 业务异常
     * @return 统一错误结果
     */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 方法级鉴权拒绝（@PreAuthorize 不通过）。
     * <p>
     * 方法级安全拦截发生在过滤器链之后，不会走 Security 的 accessDeniedHandler，
     * 必须在此转为 JSON 403，否则会被兜底异常处理器吞成 500。
     *
     * @param e 鉴权拒绝异常
     * @return 403 错误结果
     */
    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ExceptionHandler(AccessDeniedException.class)
    public Result<Void> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("访问被拒绝: {}", e.getMessage());
        return Result.fail(ResultCode.FORBIDDEN);
    }

    /**
     * 参数校验异常（@Valid 触发）。
     *
     * @param e 校验异常
     * @return 400 错误结果，携带第一条校验失败信息
     */
    @ExceptionHandler(org.springframework.web.bind.support.WebExchangeBindException.class)
    public Result<Void> handleValidException(org.springframework.web.bind.support.WebExchangeBindException e) {
        String msg = e.getFieldErrors().isEmpty() ? "参数校验失败"
                : e.getFieldErrors().get(0).getDefaultMessage();
        log.warn("参数校验失败: {}", msg);
        return Result.fail(ResultCode.PARAM_ERROR, msg);
    }

    /**
     * 兜底异常：未预期的系统错误。
     *
     * @param e 未知异常
     * @return 500 错误结果（不暴露内部细节）
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        // 系统异常必须记录完整堆栈，便于排查
        log.error("系统异常", e);
        return Result.fail(ResultCode.BIZ_ERROR, "系统繁忙，请稍后重试");
    }
}
