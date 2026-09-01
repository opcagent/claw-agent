package com.claw.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.claw.agent.model.User;

/**
 * 用户数据访问层（MyBatis Plus 自动提供 CRUD）。
 * <p>
 * 简单条件查询使用 LambdaQueryWrapper 在 service 层组装，
 * 无需编写 XML；复杂报表类 SQL 如有需要再补充自定义方法。
 */
public interface UserMapper extends BaseMapper<User> {
}
