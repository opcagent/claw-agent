package com.claw.agent.controller.agent;

import com.claw.agent.common.*;
import com.claw.agent.mapper.UserMapper;
import com.claw.agent.mapper.UserTenantMapper;
import com.claw.agent.model.McpServer;
import com.claw.agent.model.ToolConfig;
import com.claw.agent.model.User;
import com.claw.agent.model.UserTenant;
import com.claw.agent.model.dto.SkillInfo;
import com.claw.agent.model.dto.ToolKeyInfo;
import com.claw.agent.security.LoginUser;
import com.claw.agent.service.CapabilityService;
import com.claw.agent.service.ConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Agent 能力配置控制器：MCP 服务器 / 工具开关 / 技能开关。
 * <p>
 * 权限分级与 {@link ConfigController} 一致：GLOBAL 仅平台管理员、
 * TENANT 租户管理员及以上、USER 任何登录用户（仅本人）；
 * scope 为运行时入参，作用域级校验由 {@code checkScopePermission} 承担。
 * 技能接口按目标用户归属校验：本人或管理员（租户管理员限本租户）。
 */
@Tag(name = "能力查询", description = "Agent 能力与工具查询")
@RestController
@RequestMapping("/api/capability")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class CapabilityController {

    /** 操作日志模块名 */
    private static final String MODULE = "Agent能力配置";

    private final CapabilityService capabilityService;
    private final UserMapper userMapper;
    private final UserTenantMapper userTenantMapper;

    // ------------------------------------------------------------
    // MCP 服务器
    // ------------------------------------------------------------

    /** 查询某作用域下 MCP 服务器列表（敏感字段掩码） */
    @Operation(summary = "MCP 服务器列表", description = "查询某作用域下 MCP 服务器列表（敏感字段掩码）")
    @GetMapping("/mcp")
    public Mono<Result<List<McpServer>>> listMcp(@RequestParam String scope) {
        return ReactiveSupport.call(user -> {
            checkScopePermission(user, scope);
            return capabilityService.listMcpServers(scope,
                    resolveTenantId(user, scope), resolveOwnerId(user, scope));
        });
    }

    /** 保存 MCP 服务器（新增或更新；headers/env 回传掩码/空时保留原密文） */
    @Operation(summary = "保存 MCP 服务器", description = "新增或更新 MCP 服务器配置")
    @PostMapping("/mcp")
    public Mono<Result<Void>> saveMcp(@RequestBody McpServer cfg) {
        return ReactiveSupport.run(MODULE, OperType.UPDATE, "保存MCP服务器", user -> {
            checkScopePermission(user, cfg.getScope());
            cfg.setTenantId(resolveTenantId(user, cfg.getScope()));
            cfg.setOwnerId(resolveOwnerId(user, cfg.getScope()));
            capabilityService.saveMcpServer(cfg);
        });
    }

    /** 删除 MCP 服务器（仅能删除本作用域内自己可见的记录） */
    @Operation(summary = "删除 MCP 服务器", description = "删除指定 MCP 服务器（仅本作用域可见记录）")
    @DeleteMapping("/mcp/{id}")
    public Mono<Result<Void>> deleteMcp(@PathVariable Long id, @RequestParam String scope) {
        return ReactiveSupport.run(MODULE, OperType.DELETE, "删除MCP服务器", user -> {
            checkScopePermission(user, scope);
            capabilityService.deleteMcpServer(id, scope,
                    resolveTenantId(user, scope), resolveOwnerId(user, scope));
        });
    }

    // ------------------------------------------------------------
    // 工具开关
    // ------------------------------------------------------------

    /** 可开关工具目录（键/名称/说明/类型） */
    @Operation(summary = "工具目录", description = "查询可开关的工具目录（键/名称/说明/类型）")
    @GetMapping("/toolKeys")
    public Mono<Result<List<ToolKeyInfo>>> toolKeys() {
        return ReactiveSupport.call(user -> capabilityService.listToolKeys());
    }

    /** 查询某作用域下显式登记的工具开关 */
    @Operation(summary = "工具开关配置", description = "查询某作用域下显式登记的工具开关")
    @GetMapping("/toolConfigs")
    public Mono<Result<List<ToolConfig>>> listToolConfigs(@RequestParam String scope) {
        return ReactiveSupport.call(user -> {
            checkScopePermission(user, scope);
            return capabilityService.listToolConfigs(scope,
                    resolveTenantId(user, scope), resolveOwnerId(user, scope));
        });
    }

    /** 保存工具开关（启用/禁用） */
    @Operation(summary = "保存工具开关", description = "保存工具启用/禁用开关")
    @PostMapping("/toolConfig")
    public Mono<Result<Void>> saveToolConfig(@RequestBody ToolConfigRequest request) {
        return ReactiveSupport.run(MODULE, OperType.UPDATE, "保存工具开关", user -> {
            checkScopePermission(user, request.getScope());
            capabilityService.saveToolConfig(request.getScope(),
                    resolveTenantId(user, request.getScope()),
                    resolveOwnerId(user, request.getScope()),
                    request.getToolKey(), Boolean.FALSE.equals(request.getEnabled()) ? 0 : 1);
        });
    }

    // ------------------------------------------------------------
    // 技能开关
    // ------------------------------------------------------------

    /** 查询目标用户工作区技能清单（含生效启停状态；本人或管理员） */
    @Operation(summary = "技能清单", description = "查询目标用户工作区技能清单（含启停状态）")
    @GetMapping("/skills")
    public Mono<Result<List<SkillInfo>>> listSkills(@RequestParam(required = false) String username) {
        return ReactiveSupport.call(user -> {
            TargetRef target = resolveTarget(user, username);
            return capabilityService.listUserSkills(target.tenantId, target.username);
        });
    }

    /** 切换目标用户某技能的启停（写入 USER 作用域；本人或管理员） */
    @Operation(summary = "切换技能启停", description = "切换目标用户某技能的启用/禁用状态")
    @PostMapping("/skillToggle")
    public Mono<Result<Void>> toggleSkill(@RequestBody SkillToggleRequest request) {
        return ReactiveSupport.run(MODULE, OperType.UPDATE, "切换技能启停", user -> {
            TargetRef target = resolveTarget(user, request.getUsername());
            capabilityService.saveSkillConfig(ConfigService.SCOPE_USER,
                    target.tenantId, target.username,
                    request.getSkillName(), Boolean.FALSE.equals(request.getEnabled()) ? 0 : 1);
        });
    }

    // ------------------------------------------------------------
    // 权限与归属解析
    // ------------------------------------------------------------

    /** 校验当前用户是否有权操作目标作用域（与 ConfigController 同规则） */
    private void checkScopePermission(LoginUser user, String scope) {
        if (ConfigService.SCOPE_PLATFORM.equals(scope)) {
            if (!user.isAdmin()) {
                throw new BizException(ResultCode.FORBIDDEN, "仅平台管理员可维护全局配置");
            }
        } else if (ConfigService.SCOPE_TENANT.equals(scope)) {
            if (!user.isTenantAdmin()) {
                throw new BizException(ResultCode.FORBIDDEN, "仅租户管理员可维护租户配置");
            }
        }
    }

    /**
     * 解析技能接口的目标用户：入参为空即本人；
     * 他人则要求平台管理员，或租户管理员且目标在同一租户（防跨租户越权）。
     */
    private TargetRef resolveTarget(LoginUser user, String username) {
        if (!StringUtils.hasText(username) || username.equals(user.getUsername())) {
            return new TargetRef(user.getUsername(), user.getTenantId());
        }
        User target = userMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .last("LIMIT 1"));
        if (target == null) {
            throw new BizException(ResultCode.NOT_FOUND, "目标用户不存在");
        }
        if (user.isAdmin()) {
            // admin 跨租户：从 sys_user_tenant 取目标用户归属组织
            UserTenant ut = userTenantMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserTenant>()
                    .eq(UserTenant::getUserId, target.getId())
                    .eq(UserTenant::getStatus, 1)
                    .last("LIMIT 1"));
            return new TargetRef(target.getUsername(), ut != null ? ut.getTenantId() : null);
        }
        // 租户管理员：校验目标用户是否在同一组织
        UserTenant ut = userTenantMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserTenant>()
                .eq(UserTenant::getUserId, target.getId())
                .eq(UserTenant::getTenantId, user.getTenantId())
                .eq(UserTenant::getStatus, 1)
                .last("LIMIT 1"));
        if (user.isTenantAdmin() && ut != null) {
            return new TargetRef(target.getUsername(), user.getTenantId());
        }
        throw new BizException(ResultCode.FORBIDDEN, "无权管理该用户的技能");
    }

    /** 目标用户引用（username + tenantId，替代旧 User 实体上已删除的字段） */
    private record TargetRef(String username, Long tenantId) {}

    /** 解析目标作用域的租户ID（GLOBAL 固定 0；TENANT/USER 取本人租户） */
    private Long resolveTenantId(LoginUser user, String scope) {
        return ConfigService.SCOPE_PLATFORM.equals(scope) ? 0L : user.getTenantId();
    }

    /** 解析归属用户ID（仅 USER 作用域取本人） */
    private String resolveOwnerId(LoginUser user, String scope) {
        return ConfigService.SCOPE_USER.equals(scope) ? user.getUserId() : null;
    }

    /** 工具开关保存请求 */
    @Data
    public static class ToolConfigRequest {
        /** 作用域：GLOBAL / TENANT / USER */
        private String scope;
        /** 工具键 */
        private String toolKey;
        /** 是否启用 */
        private Boolean enabled;
    }

    /** 技能启停请求 */
    @Data
    public static class SkillToggleRequest {
        /** 目标用户名（空=本人） */
        private String username;
        /** 技能名 */
        private String skillName;
        /** 是否启用 */
        private Boolean enabled;
    }
}
