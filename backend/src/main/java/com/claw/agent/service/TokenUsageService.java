package com.claw.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.claw.agent.mapper.TokenUsageLogMapper;
import com.claw.agent.mapper.TokenUsageSummaryMapper;
import com.claw.agent.model.TokenUsageLog;
import com.claw.agent.model.TokenUsageSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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
        LambdaQueryWrapper<TokenUsageLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TokenUsageLog::getUserId, userId)
               .eq(TokenUsageLog::getTenantId, tenantId)
               .orderByDesc(TokenUsageLog::getUsageTime)
               .last("LIMIT " + limit);

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
               .orderByDesc(TokenUsageSummary::getTotalTokens);

        return summaryMapper.selectList(wrapper);
    }
}
