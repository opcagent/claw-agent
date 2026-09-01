package com.claw.agent.controller.agent;

import com.claw.agent.common.OperType;
import com.claw.agent.common.ReactiveSupport;
import com.claw.agent.common.Result;
import com.claw.agent.model.AgentPipeline;
import com.claw.agent.service.PipelineService;
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
 * 编排流水线控制器：三级作用域（PLATFORM / TENANT / USER）。
 * <p>
 * 方法级鉴权：任何已登录用户可访问；作用域级维护权限（平台流水线仅平台管理员、
 * 租户流水线仅本租户管理员）在 {@link PipelineService} 中按记录归属校验；
 * 响应式样板与操作日志由 {@link ReactiveSupport} 统一承担。
 */
@Tag(name = "流水线编排", description = "Agent 自动化流水线管理")
@RestController
@RequestMapping("/api/pipelines")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class PipelineController {

    /** 操作日志模块名 */
    private static final String MODULE = "流水线编排";

    private final PipelineService pipelineService;

    /** 当前用户可见的启用流水线列表（平台 + 本租户 + 本人，按顺序） */
    @Operation(summary = "流水线列表", description = "获取当前用户可见的启用流水线列表")
    @GetMapping
    public Mono<Result<List<AgentPipeline>>> list() {
        return ReactiveSupport.call(pipelineService::listVisible);
    }

    /** 新建流水线（TENANT/USER 作用域；PLATFORM 仅平台管理员） */
    @Operation(summary = "新建流水线", description = "新建自动化流水线（TENANT/USER 作用域）")
    @PostMapping
    public Mono<Result<Void>> create(@RequestBody AgentPipeline pipeline) {
        return ReactiveSupport.run(MODULE, OperType.CREATE, "新建流水线",
                u -> pipelineService.addPipeline(u, pipeline));
    }

    /** 更新流水线（平台流水线仅平台管理员可改） */
    @Operation(summary = "更新流水线", description = "更新流水线配置（平台流水线仅平台管理员可改）")
    @PutMapping("/{id}")
    public Mono<Result<Void>> update(@PathVariable Long id, @RequestBody AgentPipeline pipeline) {
        return ReactiveSupport.run(MODULE, OperType.UPDATE, "修改流水线",
                u -> pipelineService.updatePipeline(u, id, pipeline));
    }

    /** 删除流水线（平台流水线仅平台管理员可删） */
    @Operation(summary = "删除流水线", description = "删除流水线（平台流水线仅平台管理员可删）")
    @DeleteMapping("/{id}")
    public Mono<Result<Void>> delete(@PathVariable Long id) {
        return ReactiveSupport.run(MODULE, OperType.DELETE, "删除流水线",
                u -> pipelineService.deletePipeline(u, id));
    }
}
