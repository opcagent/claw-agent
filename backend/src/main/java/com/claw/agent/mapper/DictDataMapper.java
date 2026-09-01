package com.claw.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.claw.agent.model.DictData;

/**
 * 字典数据数据访问层（MyBatis Plus 自动提供 CRUD）。
 * <p>
 * 平台公共字典（tenant_id=0）与租户字典合并查询由 service 层组装。
 */
public interface DictDataMapper extends BaseMapper<DictData> {
}
