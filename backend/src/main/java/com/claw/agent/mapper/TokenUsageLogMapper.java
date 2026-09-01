package com.claw.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.claw.agent.model.TokenUsageLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * Token 使用流水 Mapper。
 */
@Mapper
public interface TokenUsageLogMapper extends BaseMapper<TokenUsageLog> {
}
