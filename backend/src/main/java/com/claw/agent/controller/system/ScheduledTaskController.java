package com.claw.agent.controller.system;

import com.claw.agent.common.OperType;
import com.claw.agent.common.ReactiveSupport;
import com.claw.agent.common.Result;
import com.claw.agent.model.ScheduledTask;
import com.claw.agent.model.ScheduledTaskLog;
import com.claw.agent.service.ScheduledTaskService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * 定时任务控制器：用户级 CRUD + 启停 + 手动执行 + 日志查询。
 * <p>
 * 所有接口需登录；操作按 userId 隔离，Service 层校验归属。
 */
@Slf4j
@Tag(name = "定时任务", description = "定时任务 CRUD/启停/手动执行")
@RestController
@RequestMapping("/api/schedule")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ScheduledTaskController {

    /** 操作日志模块名 */
    private static final String MODULE = "定时任务";

    private final ScheduledTaskService scheduledTaskService;

    /** 当前用户的任务列表（按创建时间倒序） */
    @Operation(summary = "任务列表", description = "当前用户的定时任务列表")
    @GetMapping("/list")
    public Mono<Result<List<ScheduledTask>>> list() {
        return ReactiveSupport.call(scheduledTaskService::listByUser);
    }

    /** 新建任务 */
    @Operation(summary = "新建任务", description = "新建定时任务")
    @PostMapping
    public Mono<Result<Void>> create(@RequestBody ScheduledTask task) {
        return ReactiveSupport.run(MODULE, OperType.CREATE, "新建定时任务",
                u -> scheduledTaskService.addTask(u, task));
    }

    /** 修改任务 */
    @Operation(summary = "修改任务", description = "修改定时任务配置")
    @PutMapping("/{id}")
    public Mono<Result<Void>> update(@PathVariable Long id, @RequestBody ScheduledTask task) {
        return ReactiveSupport.run(MODULE, OperType.UPDATE, "修改定时任务",
                u -> scheduledTaskService.updateTask(u, id, task));
    }

    /** 删除任务 */
    @Operation(summary = "删除任务", description = "删除指定定时任务")
    @DeleteMapping("/{id}")
    public Mono<Result<Void>> delete(@PathVariable Long id) {
        return ReactiveSupport.run(MODULE, OperType.DELETE, "删除定时任务",
                u -> scheduledTaskService.deleteTask(u, id));
    }

    /** 切换启用/禁用 */
    @Operation(summary = "切换启停", description = "切换定时任务启用/禁用状态")
    @PostMapping("/{id}/toggle")
    public Mono<Result<Void>> toggle(@PathVariable Long id) {
        return ReactiveSupport.run(MODULE, OperType.UPDATE, "切换定时任务状态",
                u -> scheduledTaskService.toggleTask(u, id));
    }

    /** 立即执行一次 */
    @Operation(summary = "立即执行", description = "立即执行一次定时任务")
    @PostMapping("/{id}/runNow")
    public Mono<Result<String>> runNow(@PathVariable Long id) {
        return ReactiveSupport.call(u -> scheduledTaskService.runNow(u, id));
    }

    /** 查询执行日志（最近 50 条） */
    @Operation(summary = "执行日志", description = "查询任务执行日志（最近 50 条）")
    @GetMapping("/{id}/logs")
    public Mono<Result<List<ScheduledTaskLog>>> logs(@PathVariable Long id) {
        return ReactiveSupport.call(u -> scheduledTaskService.listLogs(u, id));
    }
}
