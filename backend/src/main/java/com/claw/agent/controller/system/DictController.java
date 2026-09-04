package com.claw.agent.controller.system;

import com.claw.agent.common.*;
import com.claw.agent.model.DictData;
import com.claw.agent.model.DictType;
import com.claw.agent.security.LoginUser;
import com.claw.agent.service.DictService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 字典控制器：前端下拉/标签渲染的数据源（只读）+ 管理端维护（增删改）。
 * <p>
 * 读接口：任何已登录用户可查（返回已按平台 + 本租户合并收窄）。
 * 管理接口：双重鉴权——方法级按钮权限点（system:dict:add/edit/remove，
 * 平台管理员经 ROLE_ADMIN 短路）+ 作用域校验（PLATFORM 仅平台管理员；
 * TENANT 租户管理员及以上，由 {@link #resolveTenantId} 承担，同 ConfigController 模式）。
 */
@Tag(name = "数据字典", description = "字典类型与字典数据维护")
@RestController
@RequestMapping("/api/dict")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class DictController {

    /** 操作日志模块名 */
    private static final String MODULE = "字典管理";

    private final DictService dictService;

    /** 按字典类型查询启用的字典数据（平台 + 本租户合并，租户优先） */
    @Operation(summary = "按类型查询字典数据", description = "按字典类型查询启用的字典数据（平台+租户合并）")
    @GetMapping("/data/{dictType}")
    public Mono<Result<List<DictData>>> dataByType(@PathVariable String dictType) {
        return ReactiveSupport.call(u -> dictService.listDataByType(u, dictType));
    }

    // ------------------------------------------------------------
    // 管理端：字典类型 + 字典数据维护（作用域鉴权见 resolveTenantId）
    // ------------------------------------------------------------

    /** 查询指定作用域下的字典类型列表（含禁用） */
    @Operation(summary = "字典类型列表", description = "查询指定作用域下的字典类型列表")
    @GetMapping("/types")
    public Mono<Result<List<DictType>>> listTypes(@RequestParam String scope) {
        return ReactiveSupport.call(u -> dictService.listTypes(resolveTenantId(u, scope)));
    }

    /** 查询指定作用域 + 字典类型下的全部字典数据（含禁用） */
    @Operation(summary = "字典数据列表", description = "查询指定作用域+类型下的全部字典数据")
    @GetMapping("/items")
    public Mono<Result<List<DictData>>> listItems(@RequestParam String scope,
                                                  @RequestParam String dictType) {
        return ReactiveSupport.call(u -> dictService.listDataForAdmin(resolveTenantId(u, scope), dictType));
    }

    /** 新增字典类型（按钮权限点 system:dict:add；字典类型编码同租户内唯一） */
    @Operation(summary = "新增字典类型", description = "新增字典类型（编码同租户内唯一）")
    @PostMapping("/type")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('system:dict:add')")
    public Mono<Result<Void>> addType(@RequestParam String scope, @RequestBody DictType type) {
        return ReactiveSupport.run(MODULE, OperType.CREATE, "新增字典类型", u -> {
            // 租户归属从登录态强制赋值，不信任前端传参（防越权写平台/他租户字典）
            type.setId(null);
            type.setTenantId(resolveTenantId(u, scope));
            dictService.saveType(type);
        });
    }

    /** 修改字典类型（按钮权限点 system:dict:edit；类型编码不可变由服务层校验） */
    @Operation(summary = "修改字典类型", description = "修改字典类型（编码不可变）")
    @PutMapping("/type")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('system:dict:edit')")
    public Mono<Result<Void>> updateType(@RequestParam String scope, @RequestBody DictType type) {
        return ReactiveSupport.run(MODULE, OperType.UPDATE, "修改字典类型", u -> {
            type.setTenantId(resolveTenantId(u, scope));
            dictService.saveType(type);
        });
    }

    /** 删除字典类型（按钮权限点 system:dict:remove；级联删除名下字典数据） */
    @Operation(summary = "删除字典类型", description = "删除字典类型（级联删除名下数据）")
    @DeleteMapping("/type/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('system:dict:remove')")
    public Mono<Result<Void>> deleteType(@RequestParam String scope, @PathVariable Long id) {
        return ReactiveSupport.run(MODULE, OperType.DELETE, "删除字典类型",
                u -> dictService.deleteType(resolveTenantId(u, scope), id));
    }

    /** 新增字典数据（按钮权限点 system:dict:add） */
    @Operation(summary = "新增字典数据", description = "新增字典数据项")
    @PostMapping("/item")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('system:dict:add')")
    public Mono<Result<Void>> addItem(@RequestParam String scope, @RequestBody DictData data) {
        return ReactiveSupport.run(MODULE, OperType.CREATE, "新增字典数据", u -> {
            data.setId(null);
            data.setTenantId(resolveTenantId(u, scope));
            dictService.saveData(data);
        });
    }

    /** 修改字典数据（按钮权限点 system:dict:edit） */
    @Operation(summary = "修改字典数据", description = "修改字典数据项")
    @PutMapping("/item")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('system:dict:edit')")
    public Mono<Result<Void>> updateItem(@RequestParam String scope, @RequestBody DictData data) {
        return ReactiveSupport.run(MODULE, OperType.UPDATE, "修改字典数据", u -> {
            data.setTenantId(resolveTenantId(u, scope));
            dictService.saveData(data);
        });
    }

    /** 删除字典数据（按钮权限点 system:dict:remove） */
    @Operation(summary = "删除字典数据", description = "删除指定字典数据项")
    @DeleteMapping("/item/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('system:dict:remove')")
    public Mono<Result<Void>> deleteItem(@RequestParam String scope, @PathVariable Long id) {
        return ReactiveSupport.run(MODULE, OperType.DELETE, "删除字典数据",
                u -> dictService.deleteData(resolveTenantId(u, scope), id));
    }

    // ------------------------------------------------------------
    // 作用域权限与租户解析（同 ConfigController 模式）
    // ------------------------------------------------------------

    /**
     * 解析作用域对应的租户ID，同时完成权限校验：
     * PLATFORM 仅平台管理员（返回 0）；TENANT 仅租户管理员及以上（返回本人租户）。
     */
    private Long resolveTenantId(LoginUser user, String scope) {
        if (DictService.SCOPE_PLATFORM.equals(scope)) {
            if (!user.isAdmin()) {
                throw new BizException(ResultCode.FORBIDDEN, "仅平台管理员可维护平台公共字典");
            }
            return 0L;
        }
        if (DictService.SCOPE_TENANT.equals(scope)) {
            if (!user.isTenantAdmin()) {
                throw new BizException(ResultCode.FORBIDDEN, "仅租户管理员可维护租户字典");
            }
            return user.getTenantId();
        }
        throw new BizException(ResultCode.PARAM_ERROR, "未知的字典作用域：" + scope);
    }
}
