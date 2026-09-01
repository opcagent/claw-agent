package com.claw.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.claw.agent.model.Tenant;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户 Mapper（MyBatis Plus）。
 */
@Mapper
public interface TenantMapper extends BaseMapper<Tenant> {
}
