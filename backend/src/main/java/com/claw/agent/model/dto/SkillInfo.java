package com.claw.agent.model.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 技能视图项：工作区单个技能的展示信息。
 * <p>
 * 由服务层扫描用户工作区 {@code skills/<name>/SKILL.md} 解析得到，
 * {@code enabled} 为按三级作用域就近解析后的生效启停状态。
 */
@Data
@Builder
public class SkillInfo {

    /** 技能名（对应 skills/<name>/SKILL.md） */
    private String name;

    /** 技能描述（解析自 SKILL.md frontmatter） */
    private String description;

    /** 解析后是否启用（就近覆盖；无配置记录默认启用） */
    private Boolean enabled;
}
