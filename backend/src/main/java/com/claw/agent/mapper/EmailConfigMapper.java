package com.claw.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.claw.agent.model.EmailConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 邮箱配置 Mapper。
 */
@Mapper
public interface EmailConfigMapper extends BaseMapper<EmailConfig> {
}
