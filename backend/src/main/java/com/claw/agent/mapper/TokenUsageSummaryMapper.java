package com.claw.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.claw.agent.model.TokenUsageSummary;
import org.apache.ibatis.annotations.Mapper;

/**
 * Token 使用汇总 Mapper。
 */
@Mapper
public interface TokenUsageSummaryMapper extends BaseMapper<TokenUsageSummary> {
}
