package com.claw.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.claw.agent.mapper.DeptMapper;
import com.claw.agent.mapper.TokenUsageLogMapper;
import com.claw.agent.mapper.TokenUsageSummaryMapper;
import com.claw.agent.mapper.UserTenantMapper;
import com.claw.agent.model.Dept;
import com.claw.agent.model.TokenUsageLog;
import com.claw.agent.model.TokenUsageSummary;
import com.claw.agent.model.UserTenant;
import com.claw.agent.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Token 使用统计服务。
 * <p>
 * 负责记录模型调用的 Token 消耗,并提供查询统计功能。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenUsageService {

    private final TokenUsageLogMapper logMapper;
    private final TokenUsageSummaryMapper summaryMapper;
    private final UserTenantMapper userTenantMapper;
    private final DeptMapper deptMapper;

    /**
     * 记录一次模型调用的 Token 消耗。
     * <p>
     * 插入流水记录后,自动更新或创建月度汇总表(替代数据库触发器)。
     *
     * @param userId          用户ID
     * @param tenantId        租户ID
     * @param username        用户名
     * @param sessionId       会话ID
     * @param provider        模型提供商
     * @param modelName       模型名称
     * @param promptTokens    提示词 Token 数
     * @param completionTokens 回复 Token 数
     * @param requestId       请求ID
     * @param toolName        工具名称(可选)
     */
    public void recordUsage(String userId, Long tenantId, String username, String sessionId,
                           String provider, String modelName, int promptTokens, int completionTokens,
                           String requestId, String toolName) {
        LocalDateTime now = LocalDateTime.now();
        
        // Step 1: 插入流水记录
        TokenUsageLog usageLog = new TokenUsageLog();
        usageLog.setUserId(userId);
        usageLog.setTenantId(tenantId);
        usageLog.setUsername(username);
        usageLog.setSessionId(sessionId);
        usageLog.setProvider(provider);
        usageLog.setModelName(modelName);
        usageLog.setPromptTokens(promptTokens);
        usageLog.setCompletionTokens(completionTokens);
        usageLog.setTotalTokens(promptTokens + completionTokens);
        usageLog.setRequestId(requestId);
        usageLog.setToolName(toolName);
        usageLog.setUsageTime(now);

        try {
            logMapper.insert(usageLog);
            
            // Step 2: 更新或创建汇总表(替代数据库触发器)
            updateOrCreateSummary(userId, tenantId, username, promptTokens, completionTokens, now);
            
            if (log.isDebugEnabled()) {
                log.debug("已记录 Token 使用: user={}, tokens={}", username, usageLog.getTotalTokens());
            }
        } catch (Exception e) {
            log.error("记录 Token 使用失败: user={}", username, e);
            // 不抛出异常,避免影响主流程
        }
    }
    
    /**
     * 更新或创建 Token 使用汇总记录。
     * <p>
     * 替代数据库触发器逻辑,按月汇总 Token 使用量。
     *
     * @param userId           用户ID
     * @param tenantId         租户ID
     * @param username         用户名
     * @param promptTokens     提示词 Token 数
     * @param completionTokens 回复 Token 数
     * @param usageTime        使用时间
     */
    private void updateOrCreateSummary(String userId, Long tenantId, String username,
                                       int promptTokens, int completionTokens, LocalDateTime usageTime) {
        // 计算当月起止日期
        LocalDate periodStart = usageTime.toLocalDate().withDayOfMonth(1);
        LocalDate periodEnd = periodStart.withDayOfMonth(periodStart.lengthOfMonth());
        
        // 查询是否已存在该月的汇总记录
        LambdaQueryWrapper<TokenUsageSummary> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TokenUsageSummary::getUserId, userId)
               .eq(TokenUsageSummary::getTenantId, tenantId)
               .eq(TokenUsageSummary::getPeriodType, "monthly")
               .eq(TokenUsageSummary::getPeriodStart, periodStart);
        
        TokenUsageSummary existing = summaryMapper.selectOne(wrapper);
        
        if (existing != null) {
            // 已存在,累加统计
            existing.setTotalPromptTokens(existing.getTotalPromptTokens() + promptTokens);
            existing.setTotalCompletionTokens(existing.getTotalCompletionTokens() + completionTokens);
            existing.setTotalTokens(existing.getTotalTokens() + promptTokens + completionTokens);
            existing.setRequestCount(existing.getRequestCount() + 1);
            existing.setLastUpdateTime(LocalDateTime.now());
            
            summaryMapper.updateById(existing);
            
            if (log.isDebugEnabled()) {
                log.debug("已更新 Token 汇总: user={}, period={}, totalTokens={}", 
                         username, periodStart, existing.getTotalTokens());
            }
        } else {
            // 不存在,创建新记录
            TokenUsageSummary summary = new TokenUsageSummary();
            summary.setUserId(userId);
            summary.setTenantId(tenantId);
            summary.setUsername(username);
            summary.setPeriodType("monthly");
            summary.setPeriodStart(periodStart);
            summary.setPeriodEnd(periodEnd);
            summary.setTotalPromptTokens((long) promptTokens);
            summary.setTotalCompletionTokens((long) completionTokens);
            summary.setTotalTokens((long) (promptTokens + completionTokens));
            summary.setRequestCount(1);
            summary.setLastUpdateTime(LocalDateTime.now());
            
            summaryMapper.insert(summary);
            
            if (log.isDebugEnabled()) {
                log.debug("已创建 Token 汇总: user={}, period={}, totalTokens={}", 
                         username, periodStart, summary.getTotalTokens());
            }
        }
    }

    /**
     * 查询用户当前月份的 Token 使用汇总。
     *
     * @param userId   用户ID
     * @param tenantId 租户ID
     * @return Token 使用汇总,如果没有则返回 null
     */
    public TokenUsageSummary getCurrentMonthSummary(String userId, Long tenantId) {
        LocalDate now = LocalDate.now();
        LocalDate periodStart = now.withDayOfMonth(1);
        LocalDate periodEnd = now.withDayOfMonth(now.lengthOfMonth());

        LambdaQueryWrapper<TokenUsageSummary> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TokenUsageSummary::getUserId, userId)
               .eq(TokenUsageSummary::getTenantId, tenantId)
               .eq(TokenUsageSummary::getPeriodType, "monthly")
               .eq(TokenUsageSummary::getPeriodStart, periodStart);

        return summaryMapper.selectOne(wrapper);
    }

    /**
     * 查询用户指定月份的 Token 使用汇总。
     *
     * @param userId      用户ID
     * @param tenantId    租户ID
     * @param year        年份
     * @param month       月份
     * @return Token 使用汇总
     */
    public TokenUsageSummary getMonthSummary(String userId, Long tenantId, int year, int month) {
        LocalDate periodStart = LocalDate.of(year, month, 1);
        LocalDate periodEnd = periodStart.withDayOfMonth(periodStart.lengthOfMonth());

        LambdaQueryWrapper<TokenUsageSummary> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TokenUsageSummary::getUserId, userId)
               .eq(TokenUsageSummary::getTenantId, tenantId)
               .eq(TokenUsageSummary::getPeriodType, "monthly")
               .eq(TokenUsageSummary::getPeriodStart, periodStart);

        return summaryMapper.selectOne(wrapper);
    }

    /**
     * 查询用户最近 N 个月的 Token 使用汇总列表。
     *
     * @param userId   用户ID
     * @param tenantId 租户ID
     * @param months   月数
     * @return Token 使用汇总列表
     */
    public List<TokenUsageSummary> getRecentMonthsSummary(String userId, Long tenantId, int months) {
        LocalDate now = LocalDate.now();
        LocalDate startDate = now.minusMonths(months - 1).withDayOfMonth(1);

        LambdaQueryWrapper<TokenUsageSummary> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TokenUsageSummary::getUserId, userId)
               .eq(TokenUsageSummary::getTenantId, tenantId)
               .eq(TokenUsageSummary::getPeriodType, "monthly")
               .ge(TokenUsageSummary::getPeriodStart, startDate)
               .orderByDesc(TokenUsageSummary::getPeriodStart);

        return summaryMapper.selectList(wrapper);
    }

    /**
     * 查询用户 Token 使用流水(分页)。
     *
     * @param userId   用户ID
     * @param tenantId 租户ID
     * @param limit    限制条数
     * @return Token 使用流水列表
     */
    public List<TokenUsageLog> getUsageLogs(String userId, Long tenantId, int limit) {
        // 服务端限幅防拉全表
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        LambdaQueryWrapper<TokenUsageLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TokenUsageLog::getUserId, userId)
               .eq(TokenUsageLog::getTenantId, tenantId)
               .orderByDesc(TokenUsageLog::getUsageTime)
               .last("LIMIT " + safeLimit);

        return logMapper.selectList(wrapper);
    }

    /**
     * 查询租户下所有用户的 Token 使用汇总(管理员用)。
     *
     * @param tenantId 租户ID
     * @param year     年份
     * @param month    月份
     * @return 用户 Token 使用汇总列表
     */
    public List<TokenUsageSummary> getTenantUsersSummary(Long tenantId, int year, int month) {
        LocalDate periodStart = LocalDate.of(year, month, 1);

        LambdaQueryWrapper<TokenUsageSummary> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TokenUsageSummary::getTenantId, tenantId)
               .eq(TokenUsageSummary::getPeriodType, "monthly")
               .eq(TokenUsageSummary::getPeriodStart, periodStart)
               .orderByDesc(TokenUsageSummary::getTotalTokens)
               .last("LIMIT 500"); // 大租户分页请使用分页接口

        return summaryMapper.selectList(wrapper);
    }

    // ==================== 角色数据范围查询 ====================

    /**
     * 根据用户角色获取数据范围（用户ID列表）。
     * <ul>
     *   <li>平台管理员：返回 null（表示不限制，查全部）</li>
     *   <li>租户管理员：返回该租户下所有用户ID</li>
     *   <li>普通用户：返回本部门及子部门下的用户ID</li>
     * </ul>
     *
     * @param user 当前登录用户
     * @return 用户ID列表（null 表示不限制）
     */
    public List<String> resolveScopeUserIds(LoginUser user) {
        if (user.isAdmin()) {
            return null; // 平台管理员：不限制
        }
        if (user.isTenantAdmin()) {
            // 租户管理员：查本租户所有用户（从 sys_user_tenant 获取，而非 token_usage_summary）
            List<UserTenant> userTenants = userTenantMapper.selectList(
                    new LambdaQueryWrapper<UserTenant>()
                            .eq(UserTenant::getTenantId, user.getTenantId())
                            .eq(UserTenant::getStatus, 1)
                            .select(UserTenant::getUserId));
            List<String> userIds = userTenants.stream()
                    .map(UserTenant::getUserId)
                    .distinct()
                    .collect(Collectors.toList());
            // 兜底：至少包含自己
            if (userIds.isEmpty() || !userIds.contains(user.getUserId())) {
                userIds.add(user.getUserId());
            }
            return userIds;
        }
        // 普通用户：查本部门及子部门下的用户
        return getDeptAndSubDeptUserIds(user);
    }

    /**
     * 获取当前用户所在部门及子部门下的所有用户ID。
     */
    private List<String> getDeptAndSubDeptUserIds(LoginUser user) {
        // 1. 获取当前用户的部门ID
        UserTenant ut = userTenantMapper.selectOne(new LambdaQueryWrapper<UserTenant>()
                .eq(UserTenant::getUserId, user.getUserId())
                .eq(UserTenant::getTenantId, user.getTenantId())
                .last("LIMIT 1"));
        if (ut == null || ut.getDeptId() == null) {
            return Collections.singletonList(user.getUserId()); // 无部门信息，只看自己
        }
        Long deptId = ut.getDeptId();

        // 2. 获取本部门信息
        Dept dept = deptMapper.selectById(deptId);
        if (dept == null) {
            return Collections.singletonList(user.getUserId());
        }

        // 3. 查询本部门及子部门下的所有用户（通过 ancestors 前缀匹配）
        String ancestorPrefix = dept.getAncestors() + "," + deptId;
        List<Dept> deptAndChildren = deptMapper.selectList(new LambdaQueryWrapper<Dept>()
                .eq(Dept::getTenantId, user.getTenantId())
                .and(w -> w.eq(Dept::getId, deptId)
                           .or()
                           .likeRight(Dept::getAncestors, ancestorPrefix + ",")));

        List<Long> deptIds = deptAndChildren.stream()
                .map(Dept::getId)
                .collect(Collectors.toList());

        // 4. 查询这些部门下的所有用户ID
        List<UserTenant> userTenants = userTenantMapper.selectList(new LambdaQueryWrapper<UserTenant>()
                .eq(UserTenant::getTenantId, user.getTenantId())
                .in(UserTenant::getDeptId, deptIds)
                .select(UserTenant::getUserId));

        List<String> result = userTenants.stream()
                .map(UserTenant::getUserId)
                .distinct()
                .collect(Collectors.toList());

        // 兜底：部门下查不到用户时至少包含自己，避免 IN () 空列表导致 SQL 语法错误
        if (result.isEmpty() || !result.contains(user.getUserId())) {
            result.add(user.getUserId());
        }
        return result;
    }

    /**
     * 根据数据范围查询当前月份 Token 使用汇总。
     * <p>
     * 平台管理员返回全平台汇总，租户管理员返回租户汇总，普通用户返回个人汇总。
     */
    public TokenUsageSummary getCurrentMonthSummaryByScope(LoginUser user) {
        List<String> userIds = resolveScopeUserIds(user);
        LocalDate now = LocalDate.now();
        LocalDate periodStart = now.withDayOfMonth(1);

        if (userIds == null) {
            // 平台管理员：汇总全平台
            return aggregateSummary(null, user.getTenantId(), periodStart, true);
        } else if (user.isTenantAdmin()) {
            // 租户管理员：汇总本租户
            return aggregateSummary(null, user.getTenantId(), periodStart, false);
        } else {
            // 普通用户：汇总部门用户（取第一个用户的汇总作为代表，或返回聚合）
            if (userIds.size() == 1 && userIds.get(0).equals(user.getUserId())) {
                return getCurrentMonthSummary(user.getUserId(), user.getTenantId());
            }
            return aggregateSummary(userIds, user.getTenantId(), periodStart, false);
        }
    }

    /**
     * 聚合多个用户的 Token 使用汇总。
     */
    private TokenUsageSummary aggregateSummary(List<String> userIds, Long tenantId, LocalDate periodStart, boolean allTenants) {
        LambdaQueryWrapper<TokenUsageSummary> wrapper = new LambdaQueryWrapper<>();
        if (userIds != null) {
            wrapper.in(TokenUsageSummary::getUserId, userIds);
        }
        if (!allTenants && tenantId != null) {
            wrapper.eq(TokenUsageSummary::getTenantId, tenantId);
        }
        wrapper.eq(TokenUsageSummary::getPeriodType, "monthly")
               .eq(TokenUsageSummary::getPeriodStart, periodStart);

        List<TokenUsageSummary> summaries = summaryMapper.selectList(wrapper);
        if (summaries.isEmpty()) {
            return null;
        }

        // 聚合结果
        TokenUsageSummary aggregated = new TokenUsageSummary();
        long totalPrompt = 0, totalCompletion = 0, totalTokens = 0;
        int totalRequests = 0;
        for (TokenUsageSummary s : summaries) {
            totalPrompt += s.getTotalPromptTokens() != null ? s.getTotalPromptTokens() : 0;
            totalCompletion += s.getTotalCompletionTokens() != null ? s.getTotalCompletionTokens() : 0;
            totalTokens += s.getTotalTokens() != null ? s.getTotalTokens() : 0;
            totalRequests += s.getRequestCount() != null ? s.getRequestCount() : 0;
        }
        aggregated.setTotalPromptTokens(totalPrompt);
        aggregated.setTotalCompletionTokens(totalCompletion);
        aggregated.setTotalTokens(totalTokens);
        aggregated.setRequestCount(totalRequests);
        aggregated.setPeriodStart(periodStart);
        aggregated.setPeriodEnd(periodStart.withDayOfMonth(periodStart.lengthOfMonth()));
        return aggregated;
    }

    /**
     * 根据数据范围查询 Token 使用流水。
     */
    public List<TokenUsageLog> getUsageLogsByScope(LoginUser user, int limit) {
        List<String> userIds = resolveScopeUserIds(user);
        int safeLimit = Math.min(Math.max(limit, 1), 200);

        LambdaQueryWrapper<TokenUsageLog> wrapper = new LambdaQueryWrapper<>();
        if (userIds != null) {
            wrapper.in(TokenUsageLog::getUserId, userIds);
        }
        if (!user.isAdmin()) {
            wrapper.eq(TokenUsageLog::getTenantId, user.getTenantId());
        }
        wrapper.orderByDesc(TokenUsageLog::getUsageTime)
               .last("LIMIT " + safeLimit);

        return logMapper.selectList(wrapper);
    }

    /**
     * 根据数据范围查询最近 N 个月汇总。
     */
    public List<TokenUsageSummary> getRecentMonthsSummaryByScope(LoginUser user, int months) {
        List<String> userIds = resolveScopeUserIds(user);
        LocalDate now = LocalDate.now();
        LocalDate startDate = now.minusMonths(months - 1).withDayOfMonth(1);

        if (userIds == null) {
            // 平台管理员：按租户聚合（简化处理，返回全平台月度汇总）
            return getPlatformRecentMonths(startDate);
        } else if (user.isTenantAdmin()) {
            // 租户管理员：按用户聚合
            return getTenantRecentMonths(user.getTenantId(), startDate);
        } else {
            // 普通用户：返回个人汇总
            return getRecentMonthsSummary(user.getUserId(), user.getTenantId(), months);
        }
    }

    /**
     * 平台管理员：查询全平台最近 N 个月汇总（按租户聚合后按月合并）。
     */
    private List<TokenUsageSummary> getPlatformRecentMonths(LocalDate startDate) {
        LambdaQueryWrapper<TokenUsageSummary> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TokenUsageSummary::getPeriodType, "monthly")
               .ge(TokenUsageSummary::getPeriodStart, startDate)
               .orderByDesc(TokenUsageSummary::getPeriodStart);
        List<TokenUsageSummary> all = summaryMapper.selectList(wrapper);
        // 按 periodStart 分组聚合（同一月份可能有多个租户记录）
        return aggregateByMonth(all);
    }

    /**
     * 租户管理员：查询租户内最近 N 个月汇总（按用户聚合后按月合并）。
     */
    private List<TokenUsageSummary> getTenantRecentMonths(Long tenantId, LocalDate startDate) {
        LambdaQueryWrapper<TokenUsageSummary> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TokenUsageSummary::getTenantId, tenantId)
               .eq(TokenUsageSummary::getPeriodType, "monthly")
               .ge(TokenUsageSummary::getPeriodStart, startDate)
               .orderByDesc(TokenUsageSummary::getPeriodStart);
        List<TokenUsageSummary> all = summaryMapper.selectList(wrapper);
        // 按 periodStart 分组聚合（同一月份可能有多个用户记录）
        return aggregateByMonth(all);
    }

    /**
     * 将多条月度记录按 periodStart 合并为每月一条（sum 聚合）。
     */
    private List<TokenUsageSummary> aggregateByMonth(List<TokenUsageSummary> records) {
        if (records == null || records.isEmpty()) return records;
        Map<LocalDate, TokenUsageSummary> map = new LinkedHashMap<>();
        for (TokenUsageSummary r : records) {
            LocalDate period = r.getPeriodStart();
            TokenUsageSummary existing = map.get(period);
            if (existing == null) {
                map.put(period, r);
            } else {
                existing.setTotalTokens((existing.getTotalTokens() == null ? 0 : existing.getTotalTokens())
                        + (r.getTotalTokens() == null ? 0 : r.getTotalTokens()));
                existing.setTotalPromptTokens((existing.getTotalPromptTokens() == null ? 0 : existing.getTotalPromptTokens())
                        + (r.getTotalPromptTokens() == null ? 0 : r.getTotalPromptTokens()));
                existing.setTotalCompletionTokens((existing.getTotalCompletionTokens() == null ? 0 : existing.getTotalCompletionTokens())
                        + (r.getTotalCompletionTokens() == null ? 0 : r.getTotalCompletionTokens()));
                existing.setRequestCount((existing.getRequestCount() == null ? 0 : existing.getRequestCount())
                        + (r.getRequestCount() == null ? 0 : r.getRequestCount()));
            }
        }
        return new ArrayList<>(map.values());
    }

    // ==================== Token 配额检查 ====================

    /**
     * 检查用户当月 Token 使用量与配额的关系。
     * <p>
     * 返回状态信息供 SSE 流注入告警事件，不阻断对话。
     *
     * @param userId          用户ID
     * @param tenantId        租户ID
     * @param quotaLimitWan   配额上限（万 tokens，0=不限制）
     * @param warnPercent     告警阈值百分比（如 80 表示 80%）
     * @return 配额状态（包含百分比、是否告警、是否超额），配额为 0 时返回 null 表示不限制
     */
    public QuotaStatus checkQuota(String userId, Long tenantId, int quotaLimitWan, int warnPercent) {
        if (quotaLimitWan <= 0) {
            return null; // 0 = 不限制
        }
        long quotaLimit = (long) quotaLimitWan * 10_000;
        TokenUsageSummary summary = getCurrentMonthSummary(userId, tenantId);
        long usedTokens = summary != null && summary.getTotalTokens() != null ? summary.getTotalTokens() : 0;
        int percent = quotaLimit > 0 ? (int) (usedTokens * 100 / quotaLimit) : 0;
        return new QuotaStatus(usedTokens, quotaLimit, percent,
                percent >= warnPercent, percent >= 100);
    }

    /**
     * 定期清理 180 天前的 Token 使用流水。
     * <p>
     * 每月 1 日凌晨 3 点执行，释放数据库空间；汇总表不受影响。
     * 清理失败仅记日志，不影响任何业务。
     */
    @Scheduled(cron = "0 0 3 1 * ?")
    public void scheduledLogCleanup() {
        try {
            int deleted = cleanupOldLogs(180);
            log.info("定时清理 Token 流水完成: 删除 {} 条记录", deleted);
        } catch (Exception e) {
            log.error("定时清理 Token 流水失败", e);
        }
    }

    /**
     * 清理指定天数前的 Token 使用流水记录。
     * <p>
     * 由定时任务调用，释放数据库空间。汇总表不受影响。
     *
     * @param retainDays 保留天数（清理该天数之前的记录）
     * @return 删除的记录数
     */
    public int cleanupOldLogs(int retainDays) {
        if (retainDays <= 0) retainDays = 180;
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retainDays);
        LambdaQueryWrapper<TokenUsageLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.lt(TokenUsageLog::getUsageTime, cutoff);
        int deleted = logMapper.delete(wrapper);
        log.info("Token 流水清理完成: cutoff={}, deleted={}", cutoff, deleted);
        return deleted;
    }

    /**
     * Token 配额状态（不可变记录）。
     *
     * @param usedTokens   已使用 tokens
     * @param quotaLimit   配额上限
     * @param percent      使用百分比
     * @param warn         是否达到告警阈值
     * @param exceeded     是否已超额
     */
    public record QuotaStatus(long usedTokens, long quotaLimit, int percent,
                              boolean warn, boolean exceeded) {

        /** 转为前端/事件可消费的 Map */
        public Map<String, Object> toMap() {
            return Map.of(
                    "usedTokens", usedTokens,
                    "quotaLimit", quotaLimit,
                    "percent", percent,
                    "warn", warn,
                    "exceeded", exceeded
            );
        }
    }
}
