package com.claw.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.claw.agent.model.UserTenant;

/**
 * 用户-组织关联数据访问层。
 * <p>
 * 承载用户多组织成员资格的增删查操作。
 */
public interface UserTenantMapper extends BaseMapper<UserTenant> {
}
