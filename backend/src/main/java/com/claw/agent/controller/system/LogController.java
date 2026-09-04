package com.claw.agent.controller.system;

import com.claw.agent.common.ReactiveSupport;
import com.claw.agent.common.Result;
import com.claw.agent.model.LoginLog;
import com.claw.agent.model.OperLog;
import com.claw.agent.model.dto.PageResult;
import com.claw.agent.service.LoginLogService;
import com.claw.agent.service.OperLogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 日志管理控制器（需租户管理员及以上）：业务操作日志 + 登录登出日志查询。
 * <p>
 * 方法级鉴权：{@code @PreAuthorize} 限制租户管理员及以上；
 * 日志只读：写入分别由 {@code ReactiveSupport}（操作日志）与
 * {@code AuthService}（登录日志）在业务执行时完成。
 */
@Tag(name = "日志管理", description = "操作日志与登录日志查询")
@RestController
@RequestMapping("/api/adminLog")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'TENANT_ADMIN')")
public class LogController {

    private final OperLogService operLogService;
    private final LoginLogService loginLogService;

    /** 业务操作日志分页（按时间倒序；页码从 1 起，每页默认 20 条；支持搜索与筛选） */
    @Operation(summary = "操作日志分页", description = "业务操作日志分页查询（支持搜索与筛选）")
    @GetMapping("/oper/page")
    public Mono<Result<PageResult<OperLog>>> operPage(@RequestParam(defaultValue = "1") long pageNum,
                                                      @RequestParam(defaultValue = "20") long pageSize,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) String operType,
                                                      @RequestParam(required = false) Integer status) {
        return ReactiveSupport.call(u -> PageResult.from(
                operLogService.pageLogs(u, pageNum, pageSize, keyword, operType, status)));
    }

    /** 登录日志分页（按时间倒序；页码从 1 起，每页默认 20 条；支持搜索与筛选） */
    @Operation(summary = "登录日志分页", description = "登录/登出事件日志分页查询")
    @GetMapping("/login/page")
    public Mono<Result<PageResult<LoginLog>>> loginPage(@RequestParam(defaultValue = "1") long pageNum,
                                                        @RequestParam(defaultValue = "20") long pageSize,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) String eventType,
                                                        @RequestParam(required = false) Integer status) {
        return ReactiveSupport.call(u -> PageResult.from(
                loginLogService.pageLogs(u, pageNum, pageSize, keyword, eventType, status)));
    }
}
