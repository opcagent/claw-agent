package com.claw.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.claw.agent.model.Dept;

/**
 * 部门数据访问层（MyBatis Plus 自动提供 CRUD）。
 * <p>
 * 数据权限相关查询（如按 ancestors 前缀取"本部门及以下"）
 * 由 service 层用 LambdaQueryWrapper 组装。
 */
public interface DeptMapper extends BaseMapper<Dept> {
}
