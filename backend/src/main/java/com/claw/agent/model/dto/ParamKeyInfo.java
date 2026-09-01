package com.claw.agent.model.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Agent 运行参数目录项：已知配置键的元信息（说明/默认值/可选值）。
 * <p>
 * 用于管理页面「快速添加参数」：避免管理员凭空猜键名，
 * 目录与 {@code ConfigService.KEY_*} 常量保持一一对应。
 */
@Data
@Builder
public class ParamKeyInfo {

    /** 配置键（如 permission_mode） */
    private String key;

    /** 配置说明 */
    private String description;

    /** 全局默认值（未配置时生效） */
    private String defaultValue;

    /** 可选值列表（自由文本参数为 null） */
    private List<String> options;
}
