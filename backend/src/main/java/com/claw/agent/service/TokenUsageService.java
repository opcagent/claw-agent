package com.claw.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.claw.agent.mapper.TokenUsageLogMapper;
import com.claw.agent.mapper.TokenUsageSummaryMapper;
import com.claw.agent.model.TokenUsageLog;
import com.claw.agent.model.TokenUsageSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
