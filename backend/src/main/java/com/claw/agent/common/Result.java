package com.claw.agent.common;

import lombok.Data;

/**
 * 统一返回结果封装（阿里规约：对外接口统一使用 Result 包装）。
 * <p>
 * 所有 REST 接口（SSE 除外）都返回此结构，前端按 code 判断成败：
 * <pre>
 * { "code": 200, "message": "操作成功", "data": {...} }
 * </pre>
 *
 * @param <T> 业务数据类型
 */
@Data
public class Result<T> {

    /** 状态码：200 成功，其余为失败（见 ResultCode） */
    private int code;

    /** 提示信息 */
    private String message;

    /** 业务数据 */
    private T data;

    public Result() {
    }

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /** 成功（无数据） */
    public static <T> Result<T> ok() {
        return new Result<>(ResultCode.SUCCESS.getCode(), "操作成功", null);
    }

    /** 成功（带数据） */
    public static <T> Result<T> ok(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), "操作成功", data);
    }

    /** 成功（自定义提示 + 数据） */
    public static <T> Result<T> ok(String message, T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), message, data);
    }

    /** 失败（使用错误码枚举） */
    public static <T> Result<T> fail(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    /** 失败（自定义消息） */
    public static <T> Result<T> fail(ResultCode resultCode, String message) {
        return new Result<>(resultCode.getCode(), message, null);
    }

    /** 失败（自定义码 + 消息） */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }
}
