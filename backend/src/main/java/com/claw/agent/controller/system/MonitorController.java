package com.claw.agent.controller.system;

import com.claw.agent.common.ReactiveSupport;
import com.claw.agent.common.Result;
import com.claw.agent.model.dto.OnlineUserVO;
import com.claw.agent.service.MonitorService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 在线用户监控控制器（需租户管理员及以上）。
 * <p>
 * 只读接口：在线状态由 JWT 过滤器逐请求采集（最近活跃时间近似在线），
 * 平台管理员看全部租户，租户管理员仅看本租户（服务层过滤）。
 */
@Tag(name = "在线用户", description = "在线用户监控与强制下线")
@RestController
@RequestMapping("/api/adminOnline")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'TENANT_ADMIN')")
public class MonitorController {

    private final MonitorService monitorService;

    /** 活跃用户列表（保留窗口内按最近活跃时间倒序，含在线状态标记） */
    @Operation(summary = "在线用户列表", description = "活跃用户列表（含在线状态标记）")
    @GetMapping("/list")
    public Mono<Result<List<OnlineUserVO>>> onlineList() {
        return ReactiveSupport.call(monitorService::listOnlineUsers);
    }
}
