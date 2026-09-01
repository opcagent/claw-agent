package com.claw.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.claw.agent.model.AgentPreset;

/**
 * 预设 Agent 模板数据访问层（MyBatis Plus 自动提供 CRUD）。
 * <p>
 * 三级作用域可见性过滤（PLATFORM 全员 / TENANT 本租户 / USER 本人）
 * 由 service 层用 LambdaQueryWrapper 组装。
 */
public interface AgentPresetMapper extends BaseMapper<AgentPreset> {
}
