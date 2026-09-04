package com.claw.agent.controller.chat;

import com.claw.agent.common.ReactiveSupport;
import com.claw.agent.common.Result;
import com.claw.agent.model.UserQuickPhrase;
import com.claw.agent.service.QuickPhraseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 常用语/快捷指令控制器：用户级 CRUD，聊天输入框「/」面板数据来源。
 * <p>
 * 所有接口需登录；操作按 userId 隔离，无越权风险（Service 层校验归属）。
 */
@Slf4j
@Tag(name = "快捷指令", description = "快捷短语管理")
@RestController
@RequestMapping("/api/phrase")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class QuickPhraseController {

    /** 操作日志模块名 */
    private static final String MODULE = "常用语";

    private final QuickPhraseService quickPhraseService;

    /** 当前用户的常用语列表（按排序序号升序） */
    @Operation(summary = "常用语列表", description = "获取当前用户的常用语列表，按排序序号升序")
    @GetMapping("/list")
    public Mono<Result<List<UserQuickPhrase>>> list() {
        return ReactiveSupport.call(quickPhraseService::listByUser);
    }

    /** 新建常用语 */
    @Operation(summary = "新建常用语", description = "新建一条快捷常用语")
    @PostMapping
    public Mono<Result<Void>> create(@RequestBody UserQuickPhrase phrase) {
        return ReactiveSupport.run(MODULE, com.claw.agent.common.OperType.CREATE, "新建常用语",
                u -> quickPhraseService.addPhrase(u, phrase));
    }

    /** 修改常用语 */
    @Operation(summary = "修改常用语", description = "修改指定 ID 的常用语")
    @PutMapping("/{id}")
    public Mono<Result<Void>> update(@PathVariable Long id, @RequestBody UserQuickPhrase phrase) {
        return ReactiveSupport.run(MODULE, com.claw.agent.common.OperType.UPDATE, "修改常用语",
                u -> quickPhraseService.updatePhrase(u, id, phrase));
    }

    /** 删除常用语 */
    @Operation(summary = "删除常用语", description = "删除指定 ID 的常用语")
    @DeleteMapping("/{id}")
    public Mono<Result<Void>> delete(@PathVariable Long id) {
        return ReactiveSupport.run(MODULE, com.claw.agent.common.OperType.DELETE, "删除常用语",
                u -> quickPhraseService.deletePhrase(u, id));
    }
}
