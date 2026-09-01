package com.claw.agent.controller.system;

import com.claw.agent.common.OperType;
import com.claw.agent.common.ReactiveSupport;
import com.claw.agent.common.Result;
import com.claw.agent.model.Menu;
import com.claw.agent.model.dto.MenuIdsRequest;
import com.claw.agent.service.MenuService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 菜单/权限点管理控制器（平台级数据，租户共享）。
 * <p>
 * 方法级鉴权：类级要求租户管理员及以上；菜单增删改另用方法级 {@code @PreAuthorize}
 * 收紧为平台管理员专属（菜单无租户字段）；
 * 菜单与角色的关联查询/维护按当前用户租户收窄，租户管理员可操作本租户角色。
 * 业务规则（类型校验、防环、子菜单禁删、关联全量替换）在 {@link MenuService} 中实现。
 */
@Tag(name = "菜单管理", description = "菜单权限树维护")
@RestController
@RequestMapping("/api/adminMenu")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'TENANT_ADMIN')")
public class MenuController {

    /** 操作日志模块名 */
    private static final String MODULE = "菜单管理";

    private final MenuService menuService;

    /** 全部启用菜单（扁平，前端按 parentId 组树） */
    @Operation(summary = "菜单列表", description = "全部启用菜单（扁平，前端按 parentId 组树）")
    @GetMapping("/list")
    public Mono<Result<List<Menu>>> list() {
        return ReactiveSupport.call(u -> menuService.listEnabledMenus());
    }

    /** 新增菜单/按钮（平台管理员） */
    @Operation(summary = "新增菜单", description = "新增菜单或按钮权限点")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<Result<Void>> add(@RequestBody Menu menu) {
        return ReactiveSupport.run(MODULE, OperType.CREATE, "新增菜单",
                u -> menuService.addMenu(menu));
    }

    /** 修改菜单（平台管理员） */
    @Operation(summary = "修改菜单", description = "修改菜单信息")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<Result<Void>> update(@PathVariable Long id, @RequestBody Menu menu) {
        return ReactiveSupport.run(MODULE, OperType.UPDATE, "修改菜单",
                u -> menuService.updateMenu(id, menu));
    }

    /** 删除菜单（平台管理员；存在子菜单时禁止） */
    @Operation(summary = "删除菜单", description = "删除指定菜单（存在子菜单时禁止）")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<Result<Void>> delete(@PathVariable Long id) {
        return ReactiveSupport.run(MODULE, OperType.DELETE, "删除菜单",
                u -> menuService.deleteMenu(id));
    }

    /** 查询菜单已关联的角色ID列表（仅本租户角色，供关联对话框回显勾选） */
    @Operation(summary = "菜单角色列表", description = "查询菜单已关联的角色 ID 列表")
    @GetMapping("/{id}/roles")
    public Mono<Result<List<Long>>> menuRoles(@PathVariable Long id) {
        return ReactiveSupport.call(u -> menuService.listMenuRoles(u, id));
    }

    /** 保存菜单关联角色（仅本租户角色，全量替换） */
    @Operation(summary = "菜单角色授权", description = "保存菜单关联角色（全量替换）")
    @PutMapping("/{id}/roles")
    public Mono<Result<Void>> saveMenuRoles(@PathVariable Long id, @RequestBody MenuIdsRequest request) {
        return ReactiveSupport.run(MODULE, OperType.GRANT, "菜单关联角色",
                u -> menuService.saveMenuRoles(u, id, request.getRoleIds()));
    }
}
