package com.claw.agent.controller.system;

import com.claw.agent.common.OperType;
import com.claw.agent.common.ReactiveSupport;
import com.claw.agent.common.Result;
import com.claw.agent.model.dto.TenantFeatureRequest;
import com.claw.agent.service.TenantFeatureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 租户功能模块配置控制器（平台管理员专属）。
 * <p>
 * 平台管理员可为每个租户配置可用的功能模块（菜单），
 * 租户管理员只能使用平台管理员配置的功能模块。
 */
@Tag(name = "租户功能配置", description = "平台管理员为租户配置可用功能模块")
@RestController
@RequestMapping("/api/admin/tenantFeature")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class TenantFeatureController {

    /** 操作日志模块名 */
    private static final String MODULE = "租户功能配置";

    private final TenantFeatureService tenantFeatureService;

    /**
     * 查询租户的功能配置（返回启用的菜单ID列表）。
     */
    @Operation(summary = "查询租户功能配置", description = "查询指定租户的可用功能模块ID列表")
    @GetMapping("/{tenantId}")
    public Mono<Result<List<Long>>> getTenantFeatures(@PathVariable Long tenantId) {
        return ReactiveSupport.call(user ->
                tenantFeatureService.getTenantFeatureMenuIds(tenantId));
    }

    /**
     * 保存租户的功能配置（全量替换）。
     */
    @Operation(summary = "保存租户功能配置", description = "为指定租户配置可用功能模块（全量替换）")
    @PutMapping("/{tenantId}")
    public Mono<Result<Void>> saveTenantFeatures(
            @PathVariable Long tenantId,
            @RequestBody TenantFeatureRequest request) {
        return ReactiveSupport.run(MODULE, OperType.UPDATE, "保存租户功能配置",
                user -> tenantFeatureService.saveTenantFeatures(user, tenantId, request.getMenuIds()));
    }
}
