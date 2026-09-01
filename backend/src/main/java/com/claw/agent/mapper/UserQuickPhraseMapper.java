package com.claw.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.claw.agent.model.UserQuickPhrase;

/**
 * 用户常用语数据访问层（MyBatis Plus 自动提供 CRUD）。
 * <p>
 * 按 userId 过滤，无跨用户/跨租户查询需求，无需自定义 SQL。
 */
public interface UserQuickPhraseMapper extends BaseMapper<UserQuickPhrase> {
}
