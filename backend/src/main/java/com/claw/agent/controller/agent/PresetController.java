package com.claw.agent.controller.agent;

import com.claw.agent.common.OperType;
import com.claw.agent.common.ReactiveSupport;
import com.claw.agent.common.Result;
import com.claw.agent.model.AgentPreset;
import com.claw.agent.service.PresetService;
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
 * 预设 Agent 模板控制器：三级作用域（PLATFORM / TENANT / USER）。
 * <p>
 * 方法级鉴权：任何已登录用户可访问；作用域级维护权限（平台模板仅平台管理员、
 * 租户模板仅本租户管理员）在 {@link PresetService} 中按记录归属校验；
 * 响应式样板与操作日志由 {@link ReactiveSupport} 统一承担。
 */
@Tag(name = "预设模板", description = "Agent 预设模板三级作用域管理")
@RestController
@RequestMapping("/api/presets")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class PresetController {

    /** 操作日志模块名 */
    private static final String MODULE = "预设模板";

    private final PresetService presetService;

    /** 当前用户可见的预设列表（平台 + 本租户 + 本人，按顺序） */
    @Operation(summary = "预设列表", description = "获取当前用户可见的预设模板列表（平台 + 本租户 + 本人）")
    @GetMapping
    public Mono<Result<List<AgentPreset>>> list() {
        return ReactiveSupport.call(presetService::listVisible);
    }

    /** 新建预设模板（TENANT/USER 作用域；PLATFORM 仅平台管理员） */
    @Operation(summary = "新建预设", description = "新建预设模板（TENANT/USER 作用域，PLATFORM 仅平台管理员）")
    @PostMapping
    public Mono<Result<Void>> create(@RequestBody AgentPreset preset) {
        return ReactiveSupport.run(MODULE, OperType.CREATE, "新建预设模板",
                u -> presetService.addPreset(u, preset));
    }

    /** 更新预设模板（平台模板仅平台管理员可改） */
    @Operation(summary = "更新预设", description = "更新预设模板（平台模板仅平台管理员可改）")
    @PutMapping("/{id}")
    public Mono<Result<Void>> update(@PathVariable Long id, @RequestBody AgentPreset preset) {
        return ReactiveSupport.run(MODULE, OperType.UPDATE, "修改预设模板",
                u -> presetService.updatePreset(u, id, preset));
    }

    /** 删除预设模板（平台模板仅平台管理员可删） */
    @Operation(summary = "删除预设", description = "删除预设模板（平台模板仅平台管理员可删）")
    @DeleteMapping("/{id}")
    public Mono<Result<Void>> delete(@PathVariable Long id) {
        return ReactiveSupport.run(MODULE, OperType.DELETE, "删除预设模板",
                u -> presetService.deletePreset(u, id));
    }

    // ==================== 模板市场 ====================

    /** 模板市场列表（所有已发布的模板，按使用次数降序） */
    @Operation(summary = "模板市场列表", description = "获取所有已发布的模板，按使用次数降序")
    @GetMapping("/marketplace")
    public Mono<Result<List<AgentPreset>>> marketplace() {
        return ReactiveSupport.call(u -> presetService.listMarketplace());
    }

    /** 发布预设到模板市场 */
    @Operation(summary = "发布到市场", description = "发布预设模板到市场供其他用户使用")
    @PostMapping("/{id}/publish")
    public Mono<Result<Void>> publish(@PathVariable Long id) {
        return ReactiveSupport.run(MODULE, OperType.UPDATE, "发布预设到市场",
                u -> presetService.publishPreset(u, id));
    }

    /** 取消发布（从市场下架） */
    @Operation(summary = "取消发布", description = "从模板市场下架预设模板")
    @DeleteMapping("/{id}/publish")
    public Mono<Result<Void>> unpublish(@PathVariable Long id) {
        return ReactiveSupport.run(MODULE, OperType.UPDATE, "取消发布预设",
                u -> presetService.unpublishPreset(u, id));
    }

    /** 从市场使用模板（复制到个人模板 + use_count++） */
    @Operation(summary = "使用市场模板", description = "从市场使用模板，复制到个人模板并增加使用计数")
    @PostMapping("/marketplace/{id}/use")
    public Mono<Result<Void>> useFromMarketplace(@PathVariable Long id) {
        return ReactiveSupport.run(MODULE, OperType.CREATE, "从市场使用模板",
                u -> presetService.useFromMarketplace(u, id));
    }
}
