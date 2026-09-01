package com.claw.agent.controller;

import com.claw.agent.common.ReactiveSupport;
import com.claw.agent.common.Result;
import com.claw.agent.model.TokenUsageLog;
import com.claw.agent.model.TokenUsageSummary;
import com.claw.agent.security.LoginUser;
import com.claw.agent.service.TokenUsageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Token 使用统计控制器。
 * <p>
 * 提供用户 Token 使用查询和管理员统计功能。
 * <ul>
 *   <li>普通用户：只能查看自己的 Token 使用统计</li>
 *   <li>租户管理员/平台管理员：可查看本租户所有用户的 Token 使用排行</li>
 * </ul>
 */
@Tag(name = "Token 统计", description = "Token 使用量统计与查询")
@RestController
@RequestMapping("/api/tokenUsage")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class TokenUsageController {

    private final TokenUsageService tokenUsageService;

    /**
     * 查询当前用户本月 Token 使用汇总。
     */
    @Operation(summary = "本月 Token 汇总", description = "查询当前用户本月 Token 使用汇总")
    @GetMapping("/currentMonth")
    public Mono<Result<TokenUsageSummary>> getCurrentMonthSummary() {
        return ReactiveSupport.call(user ->
                tokenUsageService.getCurrentMonthSummary(user.getUserId(), user.getTenantId()));
    }

    /**
     * 查询当前用户指定月份的 Token 使用汇总。
     */
    @Operation(summary = "指定月 Token 汇总", description = "查询当前用户指定月份的 Token 使用汇总")
    @GetMapping("/month/{year}/{month}")
    public Mono<Result<TokenUsageSummary>> getMonthSummary(
            @PathVariable int year,
            @PathVariable int month) {
        return ReactiveSupport.call(user ->
                tokenUsageService.getMonthSummary(user.getUserId(), user.getTenantId(), year, month));
    }

    /**
     * 查询当前用户最近 N 个月的 Token 使用汇总列表。
     */
    @Operation(summary = "近 N 月汇总", description = "查询当前用户最近 N 个月的 Token 使用汇总列表")
    @GetMapping("/recentMonths")
    public Mono<Result<List<TokenUsageSummary>>> getRecentMonthsSummary(
            @RequestParam(defaultValue = "6") int months) {
        return ReactiveSupport.call(user ->
                tokenUsageService.getRecentMonthsSummary(user.getUserId(), user.getTenantId(), months));
    }

    /**
     * 查询当前用户的 Token 使用流水(最近 N 条)。
     */
    @Operation(summary = "Token 使用流水", description = "查询当前用户的 Token 使用流水（最近 N 条）")
    @GetMapping("/logs")
    public Mono<Result<List<TokenUsageLog>>> getUsageLogs(
            @RequestParam(defaultValue = "50") int limit) {
        return ReactiveSupport.call(user ->
                tokenUsageService.getUsageLogs(user.getUserId(), user.getTenantId(), limit));
    }

    /**
     * 管理员查询租户下所有用户本月 Token 使用汇总（仅平台/租户管理员可访问）。
     */
    @Operation(summary = "租户用户 Token 汇总", description = "管理员查询租户下所有用户本月 Token 使用汇总")
    @GetMapping("/admin/tenantUsers")
    @PreAuthorize("hasAnyRole('ADMIN', 'TENANT_ADMIN')")
    public Mono<Result<List<TokenUsageSummary>>> getTenantUsersSummary(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        return ReactiveSupport.call(user -> {
            int y = year != null ? year : java.time.LocalDate.now().getYear();
            int m = month != null ? month : java.time.LocalDate.now().getMonthValue();
            return tokenUsageService.getTenantUsersSummary(user.getTenantId(), y, m);
        });
    }

    /**
     * 测试接口：手动记录一条 Token 使用记录（用于验证链路）。
     */
    @Operation(summary = "测试记录", description = "手动记录一条 Token 使用记录（验证链路）")
    @PostMapping("/testRecord")
    public Mono<Result<String>> testRecord() {
        return ReactiveSupport.call(user -> {
            tokenUsageService.recordUsage(
                    user.getUserId(),
                    user.getTenantId(),
                    user.getUsername(),
                    "test-session-" + System.currentTimeMillis(),
                    "ollama",
                    "llama3.2",
                    100,   // prompt tokens
                    50,    // completion tokens
                    "test-request-" + System.currentTimeMillis(),
                    null   // tool name
            );
            return "已记录测试 Token 使用: 150 tokens";
        });
    }
}
