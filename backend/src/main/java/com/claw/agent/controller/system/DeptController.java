package com.claw.agent.controller.system;

import com.claw.agent.common.OperType;
import com.claw.agent.common.ReactiveSupport;
import com.claw.agent.common.Result;
import com.claw.agent.model.Dept;
import com.claw.agent.service.DeptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 部门管理控制器（租户内，需租户管理员及以上）。
 * <p>
 * 方法级鉴权：{@code @PreAuthorize} 限制租户管理员及以上；
 * 增删改接口另按按钮权限点（system:dept:*）收紧，平台管理员经 ROLE_ADMIN 短路；
 * 职责仅限协议转换：树形维护（ancestors 父链、删除校验）在
 * {@link DeptService} 中实现；响应式样板与操作日志由 {@link ReactiveSupport} 统一承担。
 */
@Tag(name = "部门管理", description = "组织架构部门维护")
@RestController
@RequestMapping("/api/adminDept")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'TENANT_ADMIN')")
public class DeptController {

    /** 操作日志模块名 */
    private static final String MODULE = "部门管理";

    private final DeptService deptService;

    /** 本租户部门列表（扁平，前端组树） */
    @Operation(summary = "部门列表", description = "本租户部门列表（扁平，前端组树）")
    @GetMapping("/list")
    public Mono<Result<List<Dept>>> list() {
        return ReactiveSupport.call(deptService::listDepts);
    }

    /** 新增部门（按钮权限点 system:dept:add） */
    @Operation(summary = "新增部门", description = "新增部门")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('system:dept:add')")
    public Mono<Result<Void>> add(@RequestBody Dept dept) {
        return ReactiveSupport.run(MODULE, OperType.CREATE, "新增部门",
                u -> deptService.addDept(u, dept));
    }

    /** 修改部门（按钮权限点 system:dept:edit） */
    @Operation(summary = "修改部门", description = "修改部门信息")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('system:dept:edit')")
    public Mono<Result<Void>> update(@PathVariable Long id, @RequestBody Dept dept) {
        return ReactiveSupport.run(MODULE, OperType.UPDATE, "修改部门",
                u -> deptService.updateDept(u, id, dept));
    }

    /** 删除部门（按钮权限点 system:dept:remove） */
    @Operation(summary = "删除部门", description = "删除指定部门（有子部门或用户时禁删）")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('system:dept:remove')")
    public Mono<Result<Void>> delete(@PathVariable Long id) {
        return ReactiveSupport.run(MODULE, OperType.DELETE, "删除部门",
                u -> deptService.deleteDept(u, id));
    }
}
