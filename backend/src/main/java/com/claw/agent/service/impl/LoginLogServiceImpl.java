package com.claw.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.claw.agent.mapper.LoginLogMapper;
import com.claw.agent.model.LoginLog;
import com.claw.agent.security.LoginUser;
import com.claw.agent.service.LoginLogService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 登录日志查询实现。
 */
@Service
public class LoginLogServiceImpl extends ServiceImpl<LoginLogMapper, LoginLog> implements LoginLogService {

    /** 分页单页条数上限 */
    private static final long MAX_PAGE_SIZE = 100;

    @Override
    public IPage<LoginLog> pageLogs(LoginUser current, long pageNum, long pageSize,
                                     String keyword, String eventType, Integer status) {
        LambdaQueryWrapper<LoginLog> wrapper = new LambdaQueryWrapper<>();
        // 平台管理员看全部，租户管理员只看本租户
        if (!current.isAdmin()) {
            wrapper.eq(LoginLog::getTenantId, current.getTenantId());
        }
        // 关键词模糊搜索：匹配用户名
        if (StringUtils.hasText(keyword)) {
            wrapper.like(LoginLog::getUsername, keyword.trim());
        }
        // 事件类型筛选
        if (StringUtils.hasText(eventType)) {
            wrapper.eq(LoginLog::getEventType, eventType.trim());
        }
        // 状态筛选
        if (status != null) {
            wrapper.eq(LoginLog::getStatus, status);
        }
        wrapper.orderByDesc(LoginLog::getId);
        // 入参收敛：防负数/超大分页拖垮数据库（分页插件 maxLimit 仅兜底）
        long safePage = Math.max(1, pageNum);
        long safeSize = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);
        return baseMapper.selectPage(new Page<>(safePage, safeSize), wrapper);
    }
}
