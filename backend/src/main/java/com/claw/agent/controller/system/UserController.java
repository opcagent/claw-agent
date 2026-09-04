package com.claw.agent.controller.system;

import com.claw.agent.common.OperType;
import com.claw.agent.common.ReactiveSupport;
import com.claw.agent.common.Result;
import com.claw.agent.model.User;
import com.claw.agent.model.dto.PageResult;
import com.claw.agent.model.dto.PasswordRequest;
import com.claw.agent.model.dto.RoleIdsRequest;
import com.claw.agent.model.dto.UserCreateRequest;
import com.claw.agent.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 用户管理控制器（租户内，需租户管理员及以上）。
 * <p>
 * 方法级鉴权：{@code @PreAuthorize} 限制租户管理员及以上；
 * 增删改/授权类接口另按按钮权限点（system:user:*）收紧，平台管理员经 ROLE_ADMIN 短路；
 * 职责仅限协议转换：业务规则（唯一性校验、租户过滤、跨租户提权防护、
 * 角色全量替换）在 {@link UserService} 中实现；
 * 响应式样板与操作日志由 {@link ReactiveSupport} 统一承担。
 */
@Tag(name = "用户管理", description = "用户 CRUD/密码重置/状态切换")
@RestController
@RequestMapping("/api/adminUser")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'TENANT_ADMIN')")
public class UserController {

    /** 操作日志模块名 */
    private static final String MODULE = "用户管理";

    private final UserService userService;

    /** 本租户用户列表（密码字段置空不下发；供下拉等全量场景，分页展示用 /page） */
    @Operation(summary = "用户列表", description = "本租户用户列表（全量，供下拉等场景）")
    @GetMapping("/list")
    public Mono<Result<List<User>>> list() {
        return ReactiveSupport.call(userService::listUsers);
    }

    /** 本租户用户分页（密码不下发；页码从 1 起，每页默认 10 条；支持关键词搜索与状态/部门筛选） */
    @Operation(summary = "用户分页", description = "本租户用户分页查询（支持关键词搜索与状态/部门筛选）")
    @GetMapping("/page")
    public Mono<Result<PageResult<User>>> page(@RequestParam(defaultValue = "1") long pageNum,
                                               @RequestParam(defaultValue = "10") long pageSize,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) Integer status,
                                               @RequestParam(required = false) Long deptId) {
        return ReactiveSupport.call(u -> PageResult.from(
                userService.pageUsers(u, pageNum, pageSize, keyword, status, deptId)));
    }

    /** 新增用户（按钮权限点 system:user:add） */
    @Operation(summary = "新增用户", description = "新增用户（按钮权限 system:user:add）")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('system:user:add')")
    public Mono<Result<Void>> add(@Valid @RequestBody UserCreateRequest request) {
        return ReactiveSupport.run(MODULE, OperType.CREATE, "新增用户",
                u -> userService.addUser(u, request));
    }

    /** 更新用户基础信息（昵称/部门/状态/备注；按钮权限点 system:user:edit） */
    @Operation(summary = "更新用户", description = "更新用户基础信息（昵称/部门/状态/备注）")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('system:user:edit')")
    public Mono<Result<Void>> update(@PathVariable String id, @RequestBody User user) {
        return ReactiveSupport.run(MODULE, OperType.UPDATE, "修改用户",
                u -> userService.updateUser(u, id, user));
    }

    /** 重置密码（按钮权限点 system:user:resetPwd） */
    @Operation(summary = "重置密码", description = "重置指定用户的登录密码")
    @PutMapping("/{id}/password")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('system:user:resetPwd')")
    public Mono<Result<Void>> resetPassword(@PathVariable String id, @Valid @RequestBody PasswordRequest request) {
        return ReactiveSupport.run(MODULE, OperType.UPDATE, "重置密码",
                u -> userService.resetPassword(u, id, request.getNewPassword()));
    }

    /** 删除用户（按钮权限点 system:user:remove） */
    @Operation(summary = "删除用户", description = "删除指定用户")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('system:user:remove')")
    public Mono<Result<Void>> delete(@PathVariable String id) {
        return ReactiveSupport.run(MODULE, OperType.DELETE, "删除用户",
                u -> userService.deleteUser(u, id));
    }

    /** 查询用户已分配的角色ID列表（供分配对话框回显勾选） */
    @Operation(summary = "用户角色列表", description = "查询用户已分配的角色 ID 列表")
    @GetMapping("/{id}/roles")
    public Mono<Result<List<Long>>> userRoles(@PathVariable String id) {
        return ReactiveSupport.call(u -> userService.listUserRoles(u, id));
    }

    /** 保存用户角色分配（全量替换；仅允许分配本租户角色；按钮权限点 system:user:grant） */
    @Operation(summary = "分配角色", description = "保存用户角色分配（全量替换）")
    @PutMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('system:user:grant')")
    public Mono<Result<Void>> saveUserRoles(@PathVariable String id, @RequestBody RoleIdsRequest request) {
        return ReactiveSupport.run(MODULE, OperType.GRANT, "分配用户角色",
                u -> userService.saveUserRoles(u, id, request.getRoleIds()));
    }
}
