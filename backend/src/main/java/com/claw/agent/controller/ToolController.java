package com.claw.agent.controller;

import com.claw.agent.common.Result;
import com.claw.agent.config.tool.ToolRegistry;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * 工具集管理控制器。
 * <p>
 * 提供工具集的查询、启用、禁用等管理接口。
 */
@Slf4j
@Tag(name = "工具管理", description = "Agent 工具集查询与详情")
@RestController
@RequestMapping("/api/tools")
@RequiredArgsConstructor
public class ToolController {

    private final ToolRegistry toolRegistry;

    /**
     * 获取所有工具集列表（包含工具详情）。
     *
     * @return 工具集元数据列表，每个包含 tools 字段
     */
    @Operation(summary = "工具集详情列表", description = "获取所有工具集列表（包含工具详情）")
    @GetMapping("/details")
    public Mono<Result<List<ToolRegistry.ToolMetadataWithDetails>>> listAllWithDetails() {
        return Mono.fromCallable(() -> Result.ok(toolRegistry.getAllToolSetsWithDetails()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 获取指定工具集的工具详情。
     *
     * @param code 工具集代码
     * @return 工具详情列表
     */
    @Operation(summary = "工具集工具详情", description = "获取指定工具集的工具详情列表")
    @GetMapping("/{code}/tools")
    public Mono<Result<List<com.claw.agent.config.tool.ToolDetailExtractor.ToolDetail>>> getTools(@PathVariable String code) {
        return Mono.fromCallable(() -> Result.ok(toolRegistry.getToolDetails(code)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 获取所有工具集列表。
     *
     * @return 工具集元数据列表
     */
    @Operation(summary = "工具集列表", description = "获取所有工具集元数据列表")
    @GetMapping("/list")
    public Mono<Result<List<ToolRegistry.ToolMetadata>>> listAll() {
        return Mono.fromCallable(() -> Result.ok(toolRegistry.getAllToolSets()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 获取已启用的工具集列表。
     *
     * @return 已启用的工具集代码列表
     */
    @Operation(summary = "已启用工具集", description = "获取已启用的工具集代码列表")
    @GetMapping("/enabled")
    public Mono<Result<List<String>>> listEnabled() {
        return Mono.fromCallable(() -> Result.ok(List.copyOf(toolRegistry.getEnabledToolCodes())))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 按分类获取工具集。
     * <p>
     * 支持的分类: utility(实用工具)、search(搜索)、data(数据处理)、code(代码相关)、ai(AI 增强)、system(系统管理)
     *
     * @param category 分类名称
     * @return 该分类下的工具集列表
     */
    @Operation(summary = "按分类查询", description = "按分类获取工具集（utility/search/data/code/ai/system）")
    @GetMapping("/category/{category}")
    public Mono<Result<List<ToolRegistry.ToolMetadata>>> getByCategory(@PathVariable String category) {
        return Mono.fromCallable(() -> Result.ok(toolRegistry.getToolSetsByCategory(category)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 获取单个工具集详情。
     *
     * @param code 工具集代码
     * @return 工具集元数据
     */
    @Operation(summary = "工具集详情", description = "获取单个工具集元数据详情")
    @GetMapping("/{code}")
    public Mono<Result<ToolRegistry.ToolMetadata>> getDetail(@PathVariable String code) {
        return Mono.fromCallable(() -> {
            ToolRegistry.ToolMetadata metadata = toolRegistry.getToolSet(code);
            if (metadata == null) {
                return Result.<ToolRegistry.ToolMetadata>fail(404, "工具集不存在: " + code);
            }
            return Result.ok(metadata);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 启用工具集。
     *
     * @param code 工具集代码
     * @return 操作结果
     */
    @Operation(summary = "启用工具集", description = "启用指定工具集")
    @PostMapping("/{code}/enable")
    public Mono<Result<Void>> enable(@PathVariable String code) {
        return Mono.fromCallable(() -> {
            try {
                toolRegistry.enableToolSet(code);
                return Result.<Void>ok();
            } catch (Exception e) {
                log.error("启用工具集失败: {}", code, e);
                return Result.<Void>fail(500, e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 禁用工具集。
     *
     * @param code 工具集代码
     * @return 操作结果
     */
    @Operation(summary = "禁用工具集", description = "禁用指定工具集")
    @PostMapping("/{code}/disable")
    public Mono<Result<Void>> disable(@PathVariable String code) {
        return Mono.fromCallable(() -> {
            try {
                toolRegistry.disableToolSet(code);
                return Result.<Void>ok();
            } catch (Exception e) {
                log.error("禁用工具集失败: {}", code, e);
                return Result.<Void>fail(500, e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 批量启用工具集。
     *
     * @param codes 工具集代码列表
     * @return 操作结果
     */
    @Operation(summary = "批量启用", description = "批量启用多个工具集")
    @PostMapping("/batch-enable")
    public Mono<Result<Void>> batchEnable(@RequestBody List<String> codes) {
        return Mono.fromCallable(() -> {
            int successCount = 0;
            int failCount = 0;
            for (String code : codes) {
                try {
                    toolRegistry.enableToolSet(code);
                    successCount++;
                } catch (Exception e) {
                    log.warn("启用工具集失败: {}", code, e);
                    failCount++;
                }
            }
            return Result.<Void>ok(String.format("成功启用 %d 个，失败 %d 个", successCount, failCount), null);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 批量禁用工具集。
     *
     * @param codes 工具集代码列表
     * @return 操作结果
     */
    @Operation(summary = "批量禁用", description = "批量禁用多个工具集")
    @PostMapping("/batch-disable")
    public Mono<Result<Void>> batchDisable(@RequestBody List<String> codes) {
        return Mono.fromCallable(() -> {
            int successCount = 0;
            int failCount = 0;
            for (String code : codes) {
                try {
                    toolRegistry.disableToolSet(code);
                    successCount++;
                } catch (Exception e) {
                    log.warn("禁用工具集失败: {}", code, e);
                    failCount++;
                }
            }
            return Result.<Void>ok(String.format("成功禁用 %d 个，失败 %d 个", successCount, failCount), null);
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
