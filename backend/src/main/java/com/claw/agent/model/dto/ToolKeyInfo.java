package com.claw.agent.model.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 工具目录项：可开关工具的元信息（名称/说明/类型）。
 * <p>
 * 用于管理页面展示可开关的工具清单；目录与 {@code CapabilityService.TOOL_*}
 * 常量一一对应，新增可开关工具时必须同步登记。
 */
@Data
@Builder
public class ToolKeyInfo {

    /** 工具键（如 filesystem / shell / note_tools） */
    private String key;

    /** 工具中文名 */
    private String name;

    /** 工具说明 */
    private String description;

    /** 类型：builtin 内置 / custom 自定义 */
    private String type;
}
