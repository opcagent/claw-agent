package com.claw.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.claw.agent.mapper.OperLogMapper;
import com.claw.agent.model.OperLog;
import com.claw.agent.security.LoginUser;
import com.claw.agent.service.OperLogService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 业务操作日志查询实现。
 */
@Service
public class OperLogServiceImpl extends ServiceImpl<OperLogMapper, OperLog> implements OperLogService {

    /** 分页单页条数上限 */
    private static final long MAX_PAGE_SIZE = 100;

    @Override
    public IPage<OperLog> pageLogs(LoginUser current, long pageNum, long pageSize,
                                    String keyword, String operType, Integer status) {
        LambdaQueryWrapper<OperLog> wrapper = new LambdaQueryWrapper<>();
        // 平台管理员看全部，租户管理员只看本租户
        if (!current.isAdmin()) {
            wrapper.eq(OperLog::getTenantId, current.getTenantId());
        }
        // 关键词模糊搜索：匹配操作人/模块/描述
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(OperLog::getOperName, kw)
                    .or().like(OperLog::getModule, kw)
                    .or().like(OperLog::getOperDesc, kw));
        }
        // 操作类型筛选
        if (StringUtils.hasText(operType)) {
            wrapper.eq(OperLog::getOperType, operType.trim());
        }
        // 状态筛选
        if (status != null) {
            wrapper.eq(OperLog::getStatus, status);
        }
        wrapper.orderByDesc(OperLog::getId);
        // 入参收敛：防负数/超大分页拖垮数据库（分页插件 maxLimit 仅兜底）
        long safePage = Math.max(1, pageNum);
        long safeSize = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);
        return baseMapper.selectPage(new Page<>(safePage, safeSize), wrapper);
    }
}
