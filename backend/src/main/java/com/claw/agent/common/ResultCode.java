package com.claw.agent.common;

import lombok.Getter;

/**
 * 业务错误码枚举（阿里规约：错误码统一管理，避免魔法数字散落各处）。
 */
@Getter
public enum ResultCode {

    /** 操作成功 */
    SUCCESS(200, "操作成功"),

    /** 请求参数错误 */
    PARAM_ERROR(400, "请求参数错误"),

    /** 未认证（未登录或 token 失效） */
    UNAUTHORIZED(401, "未认证，请先登录"),

    /** 无权限（RBAC 拦截） */
    FORBIDDEN(403, "无权限访问"),

    /** 资源不存在 */
    NOT_FOUND(404, "资源不存在"),

    /** 业务处理失败 */
    BIZ_ERROR(500, "业务处理失败"),

    /** 用户名或密码错误 */
    LOGIN_FAILED(1001, "用户名或密码错误"),

    /** 账号已被禁用 */
    USER_DISABLED(1002, "账号已被禁用"),

    /** 用户名已存在 */
    USER_EXISTS(1003, "用户名已存在"),

    /** 文件上传失败 */
    UPLOAD_ERROR(2001, "文件上传失败"),

    /** 文件大小超限 */
    FILE_TOO_LARGE(2002, "文件大小超出限制"),

    /** Agent 调用失败 */
    AGENT_ERROR(3001, "Agent 调用失败"),

    // ===== 流水线模块（4xxx）=====

    /** 流水线不存在 */
    PIPELINE_NOT_FOUND(4001, "流水线不存在"),

    /** 流水线编码已存在（同作用域三元组内重复） */
    PIPELINE_CODE_EXISTS(4002, "流水线编码已存在"),

    /** 流水线已禁用（对话选择时不可用） */
    PIPELINE_DISABLED(4003, "流水线不存在或已禁用"),

    // ===== 预设模板模块（5xxx）=====

    /** 预设模板不存在 */
    PRESET_NOT_FOUND(5001, "预设模板不存在"),

    /** 预设编码已存在（同作用域三元组内重复） */
    PRESET_CODE_EXISTS(5002, "预设编码已存在"),

    /** 预设模板已禁用（对话选择时不可用） */
    PRESET_DISABLED(5003, "预设模板不存在或已禁用"),

    // ===== 系统级（6xxx）=====

    /** 服务正在维护（优雅停机等场景） */
    SERVICE_UNAVAILABLE(503, "服务正在更新中，请稍后重试"),

    /** 请求过于频繁（限流） */
    RATE_LIMITED(429, "请求过于频繁，请稍后再试");

    /** 错误码 */
    private final int code;

    /** 错误信息 */
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
