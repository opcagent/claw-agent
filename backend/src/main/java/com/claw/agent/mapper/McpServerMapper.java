package com.claw.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.claw.agent.model.McpServer;
import org.apache.ibatis.annotations.Mapper;

/**
 * MCP 服务器登记 Mapper（MyBatis Plus）。
 */
@Mapper
public interface McpServerMapper extends BaseMapper<McpServer> {
}
