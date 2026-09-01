package com.claw.agent.controller.auth;

import com.claw.agent.common.IpContextHolder;
import com.claw.agent.common.OperType;
import com.claw.agent.common.ReactiveSupport;
import com.claw.agent.common.Result;
import com.claw.agent.config.security.ClientIpFilter;
import com.claw.agent.config.infra.TraceFilter;
import com.claw.agent.model.LoginLog;
import com.claw.agent.model.Menu;
import com.claw.agent.model.dto.ChangePasswordRequest;
import com.claw.agent.model.dto.LoginRequest;
import com.claw.agent.model.dto.LoginResponse;
import com.claw.agent.model.dto.ProfileResponse;
import com.claw.agent.model.dto.ProfileUpdateRequest;
import com.claw.agent.service.AuthService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * 认证控制器：登录 / 修改密码 / 登出 / 当前用户信息与可见菜单。
 * <p>
 * 方法级鉴权：类级要求已登录；登录为匿名接口，方法级 {@code permitAll()} 覆盖。
 * 账号由管理员在用户管理中创建，不提供自助注册。
 * 业务逻辑（校验、签发、登录日志）全部下沉 {@link AuthService}，
 * 本类只做协议转换。
 */
@Tag(name = "认证管理", description = "登录/登出/注册/个人信息")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AuthController {

    private final AuthService authService;

    /** 登录：校验账号密码，签发 JWT，下发角色与权限（匿名放行，登录日志含访问者 IP） */
    @Operation(summary = "用户登录", description = "校验账号密码，签发 JWT，下发角色与权限")
    @PostMapping("/login")
    @PreAuthorize("permitAll()")
    public Mono<Result<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        // 匿名接口不走 ReactiveSupport：从上下文取出 IP 与 traceId 自行桥接到业务线程，供登录日志落库与 MDC 跟踪
        return Mono.deferContextual(ctxView -> {
            String clientIp = ctxView.getOrDefault(ClientIpFilter.CONTEXT_KEY, null);
            String traceId = ctxView.getOrDefault(TraceFilter.CONTEXT_KEY, null);
            return Mono.fromCallable(() -> {
                IpContextHolder.set(clientIp);
                ReactiveSupport.putTrace(traceId);
                try {
                    return Result.ok(authService.login(request));
                } finally {
                    IpContextHolder.clear();
                    MDC.remove(TraceFilter.MDC_KEY);
                }
            }).subscribeOn(Schedulers.boundedElastic());
        });
    }

    /** 修改本人登录密码（操作对象取自 JWT，不信任前端传参） */
    @Operation(summary = "修改密码", description = "修改当前登录用户的登录密码")
    @PutMapping("/password")
    public Mono<Result<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return ReactiveSupport.run("个人中心", OperType.UPDATE, "修改密码",
                u -> authService.changePassword(u, request));
    }

    /** 当前用户可见菜单树（目录/菜单），驱动前端导航渲染 */
    @Operation(summary = "当前用户菜单", description = "获取当前用户可见的菜单树，驱动前端导航渲染")
    @GetMapping("/menus")
    public Mono<Result<List<Menu>>> myMenus() {
        return ReactiveSupport.call(authService::listMyMenus);
    }

    /** 当前登录用户信息（前端刷新页面时回显） */
    @Operation(summary = "当前登录信息", description = "获取当前登录用户信息（前端刷新页面时回显）")
    @GetMapping("/info")
    public Mono<Result<LoginResponse>> info() {
        return ReactiveSupport.call(authService::currentUserInfo);
    }

    /** 本人个人信息详情（基础资料 + 最近一次成功登录） */
    @Operation(summary = "个人信息详情", description = "获取本人基础资料与最近一次成功登录记录")
    @GetMapping("/profile")
    public Mono<Result<ProfileResponse>> profile() {
        return ReactiveSupport.call(authService::profile);
    }

    /** 本人资料自助更新（仅昵称与联系方式；操作对象取自 JWT） */
    @Operation(summary = "更新个人资料", description = "自助更新昵称与联系方式")
    @PutMapping("/profile")
    public Mono<Result<Void>> updateProfile(@RequestBody ProfileUpdateRequest request) {
        return ReactiveSupport.run("个人中心", OperType.UPDATE, "修改个人资料",
                u -> authService.updateProfile(u, request));
    }

    /** 本人最近登录记录（含失败，时间倒序；条数服务端限幅） */
    @Operation(summary = "最近登录记录", description = "查看本人最近登录记录（含失败），时间倒序")
    @GetMapping("/login-logs")
    public Mono<Result<List<LoginLog>>> myLoginLogs(
            @RequestParam(name = "limit", defaultValue = "10") int limit) {
        return ReactiveSupport.call(u -> authService.myLoginLogs(u, limit));
    }

    /** 登出：记录登出日志（JWT 无状态，前端丢弃 token 即完成登出） */
    @Operation(summary = "用户登出", description = "记录登出日志，前端丢弃 token 即完成登出")
    @PostMapping("/logout")
    public Mono<Result<Void>> logout() {
        return ReactiveSupport.run(authService::logout);
    }
}
