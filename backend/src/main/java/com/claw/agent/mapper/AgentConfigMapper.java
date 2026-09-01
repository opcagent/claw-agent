package com.claw.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.claw.agent.model.AgentConfigItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 运行参数 Mapper（MyBatis Plus）。
 */
@Mapper
public interface AgentConfigMapper extends BaseMapper<AgentConfigItem> {
}
