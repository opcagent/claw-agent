package com.claw.agent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.claw.agent.model.AgentPipeline;
import com.claw.agent.security.LoginUser;

import java.util.List;

/**
 * 编排流水线服务接口（三级作用域 PLATFORM / TENANT / USER）。
 * <p>
 * 流水线把多步骤任务固化为可复用执行剧本；对话时选择流水线后，
 * 其步骤与异常处理策略随用户消息注入当轮上下文，由主 Agent 依次执行。
 * 可见性与维护权限范式与预设模板一致：平台全员可见仅平台管理员可维护、
 * 租户模板本租户可见仅本租户管理员可维护、用户模板仅本人可见可维护。
 */
public interface PipelineService extends IService<AgentPipeline> {

    /**
     * 当前用户可见的启用流水线列表（平台 + 本租户 + 本人），按显示顺序。
     *
     * @param current 当前登录用户
     * @return 流水线列表
     */
    List<AgentPipeline> listVisible(LoginUser current);

    /**
     * 新建流水线（编码同作用域内唯一，越权作用域直接拒绝）。
     *
     * @param current  当前登录用户
     * @param pipeline 流水线内容（作用域决定归属）
     */
    void addPipeline(LoginUser current, AgentPipeline pipeline);

    /**
     * 更新流水线（编码与归属不可变，仅内容/顺序/启停）。
     *
     * @param current  当前登录用户
     * @param id       目标流水线ID
     * @param pipeline 更新内容
     */
    void updatePipeline(LoginUser current, Long id, AgentPipeline pipeline);

    /**
     * 删除流水线（平台内置种子数据同样可删，属平台管理员自治范围）。
     *
     * @param current 当前登录用户
     * @param id      目标流水线ID
     */
    void deletePipeline(LoginUser current, Long id);

    /**
     * 对话发起时按编码解析当前用户可用的启用流水线（可见性同列表规则）。
     *
     * @param current      当前登录用户
     * @param pipelineCode 流水线编码
     * @return 命中的流水线（不存在或不可见返回 null，调用方自行报错）
     */
    AgentPipeline resolveEnabled(LoginUser current, String pipelineCode);
}
