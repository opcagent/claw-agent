package com.claw.agent.controller.system;

import com.claw.agent.common.OperType;
import com.claw.agent.common.ReactiveSupport;
import com.claw.agent.common.Result;
import com.claw.agent.model.Tenant;
import com.claw.agent.model.User;
import com.claw.agent.model.dto.SetAdminRequest;
import com.claw.agent.model.dto.TenantCreateWithAdminRequest;
import com.claw.agent.service.TenantService;
import com.claw.agent.service.UserService;
import jakarta.validation.Valid;
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
 * 租户管理控制器（平台级，仅平台管理员可操作）。
 * <p>
 * 方法级鉴权：{@code @PreAuthorize} 收紧为仅平台管理员（路径级仅要求租户管理员及以上）；
 * 业务规则（编码唯一、有用户禁删）在 {@link TenantService} 中实现。
 */
@Tag(name = "租户管理", description = "多租户管理（仅平台管理员）")
@RestController
@RequestMapping("/api/adminTenant")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class TenantController {

    /** 操作日志模块名 */
    private static final String MODULE = "租户管理";

    private final TenantService tenantService;
    private final UserService userService;

    /** 全部租户列表 */
    @Operation(summary = "租户列表", description = "全部租户列表")
    @GetMapping("/list")
    public Mono<Result<List<Tenant>>> list() {
        return ReactiveSupport.call(u -> tenantService.listTenants());
    }

    /** 新增租户（传统方式，不创建管理员） */
    @Operation(summary = "新增租户", description = "新增租户（传统方式，不创建管理员）")
    @PostMapping
    public Mono<Result<Void>> add(@RequestBody Tenant tenant) {
        return ReactiveSupport.run(MODULE, OperType.CREATE, "新增租户",
                u -> tenantService.addTenant(u, tenant));
    }

    /** 新增租户并创建初始管理员（推荐方式） */
    @Operation(summary = "新增租户并创建管理员", description = "新增租户并创建初始管理员（推荐方式）")
    @PostMapping("/withAdmin")
    public Mono<Result<Void>> addWithAdmin(@Valid @RequestBody TenantCreateWithAdminRequest request) {
        return ReactiveSupport.run(MODULE, OperType.CREATE, "新增租户并创建管理员",
                u -> tenantService.addTenantWithAdmin(u, request));
    }

    /** 修改租户 */
    @Operation(summary = "修改租户", description = "修改租户信息")
    @PutMapping("/{id}")
    public Mono<Result<Void>> update(@PathVariable Long id, @RequestBody Tenant tenant) {
        return ReactiveSupport.run(MODULE, OperType.UPDATE, "修改租户",
                u -> tenantService.updateTenant(id, tenant));
    }

    /** 删除租户（已禁用：仅支持通过修改接口将 status 置为 0 来停用） */
    @Operation(summary = "删除租户", description = "已禁用：租户不支持删除，请通过修改接口将 status 置为 0 来停用")
    @DeleteMapping("/{id}")
    public Mono<Result<Void>> delete(@PathVariable Long id) {
        return ReactiveSupport.run(MODULE, OperType.DELETE, "删除租户",
                u -> tenantService.deleteTenant(id));
    }

    /** 设置租户管理员（平台管理员专用） */
    @Operation(summary = "设置租户管理员", description = "设置租户管理员（平台管理员专用）")
    @PutMapping("/{id}/admin")
    public Mono<Result<Void>> setAdmin(@PathVariable Long id, @Valid @RequestBody SetAdminRequest request) {
        return ReactiveSupport.run(MODULE, OperType.GRANT, "设置租户管理员",
                u -> tenantService.setTenantAdmin(u, id, request));
    }

    /** 查询指定租户的用户列表（供设置管理员等场景，密码不下发） */
    @Operation(summary = "租户用户列表", description = "查询指定租户的用户列表")
    @GetMapping("/{id}/users")
    public Mono<Result<List<User>>> tenantUsers(@PathVariable Long id) {
        return ReactiveSupport.call(u -> userService.listUsersByTenant(u, id));
    }
}
