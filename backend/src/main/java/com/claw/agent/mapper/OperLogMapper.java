package com.claw.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.claw.agent.model.OperLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 业务操作日志数据访问层。
 */
@Mapper
public interface OperLogMapper extends BaseMapper<OperLog> {
}
