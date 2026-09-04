package com.claw.agent.controller.system;

import com.claw.agent.common.OperType;
import com.claw.agent.common.ReactiveSupport;
import com.claw.agent.common.Result;
import com.claw.agent.model.Role;
import com.claw.agent.model.dto.MenuIdsRequest;
import com.claw.agent.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 角色管理控制器（租户内，需租户管理员及以上）。
 * <p>
 * 方法级鉴权：{@code @PreAuthorize} 限制租户管理员及以上；
 * 增删改/授权类接口另按按钮权限点（system:role:*）收紧，平台管理员经 ROLE_ADMIN 短路；
 * 职责仅限协议转换：唯一性校验、删除保护、菜单授权全量替换在
 * {@link RoleService} 中实现；响应式样板与操作日志由 {@link ReactiveSupport} 统一承担。
 */
@Tag(name = "角色管理", description = "RBAC 角色与权限分配")
@RestController
@RequestMapping("/api/adminRole")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'TENANT_ADMIN')")
public class RoleController {

    /** 操作日志模块名 */
    private static final String MODULE = "角色管理";

    private final RoleService roleService;

    /** 本租户角色列表（按显示顺序） */
    @Operation(summary = "角色列表", description = "本租户角色列表（按显示顺序）")
    @GetMapping("/list")
    public Mono<Result<List<Role>>> list() {
        return ReactiveSupport.call(roleService::listRoles);
    }

    /** 新增角色（按钮权限点 system:role:add） */
    @Operation(summary = "新增角色", description = "新增 RBAC 角色")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('system:role:add')")
    public Mono<Result<Void>> add(@RequestBody Role role) {
        return ReactiveSupport.run(MODULE, OperType.CREATE, "新增角色",
                u -> roleService.addRole(u, role));
    }

    /** 修改角色（按钮权限点 system:role:edit） */
    @Operation(summary = "修改角色", description = "修改角色名称与权限字符")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('system:role:edit')")
    public Mono<Result<Void>> update(@PathVariable Long id, @RequestBody Role role) {
        return ReactiveSupport.run(MODULE, OperType.UPDATE, "修改角色",
                u -> roleService.updateRole(u, id, role));
    }

    /** 删除角色（按钮权限点 system:role:remove） */
    @Operation(summary = "删除角色", description = "删除指定角色（有用户关联时禁删）")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('system:role:remove')")
    public Mono<Result<Void>> delete(@PathVariable Long id) {
        return ReactiveSupport.run(MODULE, OperType.DELETE, "删除角色",
                u -> roleService.deleteRole(u, id));
    }

    /** 查询角色已授权的菜单ID列表（供前端授权对话框回显勾选） */
    @Operation(summary = "角色菜单列表", description = "查询角色已授权的菜单 ID 列表")
    @GetMapping("/{id}/menus")
    public Mono<Result<List<Long>>> roleMenus(@PathVariable Long id) {
        return ReactiveSupport.call(u -> roleService.listRoleMenus(u, id));
    }

    /** 保存角色菜单授权（全量替换；按钮权限点 system:role:grant） */
    @Operation(summary = "角色菜单授权", description = "保存角色菜单授权（全量替换）")
    @PutMapping("/{id}/menus")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('system:role:grant')")
    public Mono<Result<Void>> saveRoleMenus(@PathVariable Long id, @RequestBody MenuIdsRequest request) {
        return ReactiveSupport.run(MODULE, OperType.GRANT, "角色菜单授权",
                u -> roleService.saveRoleMenus(u, id, request.getMenuIds()));
    }
}
