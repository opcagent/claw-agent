package com.claw.agent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.claw.agent.model.AgentPreset;
import com.claw.agent.security.LoginUser;

import java.util.List;

/**
 * 预设 Agent 模板服务：三级作用域（PLATFORM / TENANT / USER）的可见性与维护权限。
 */
public interface PresetService extends IService<AgentPreset> {

    /**
     * 当前用户可见的预设列表（平台 + 本租户 + 本人，按顺序）。
     *
     * @param current 当前登录用户
     * @return 启用的预设列表
     */
    List<AgentPreset> listVisible(LoginUser current);

    /**
     * 新建预设模板（作用域写权限校验 + 归属填充）。
     *
     * @param current 当前登录用户
     * @param preset  模板内容
     */
    void addPreset(LoginUser current, AgentPreset preset);

    /**
     * 更新预设模板（归属校验后覆盖内容字段）。
     *
     * @param current 当前登录用户
     * @param id      模板ID
     * @param preset  模板内容
     */
    void updatePreset(LoginUser current, Long id, AgentPreset preset);

    /**
     * 删除预设模板（归属校验）。
     *
     * @param current 当前登录用户
     * @param id      模板ID
     */
    void deletePreset(LoginUser current, Long id);

    /**
     * 发布预设到模板市场（将模板复制为 PLATFORM 作用域，供所有用户浏览使用）。
     *
     * @param current 当前登录用户
     * @param id      模板ID
     */
    void publishPreset(LoginUser current, Long id);

    /**
     * 取消发布（从市场下架）。
     *
     * @param current 当前登录用户
     * @param id      模板ID
     */
    void unpublishPreset(LoginUser current, Long id);

    /**
     * 模板市场列表（所有已发布的模板，按使用次数降序）。
     *
     * @return 已发布模板列表
     */
    List<AgentPreset> listMarketplace();

    /**
     * 从市场使用模板：复制一份到当前用户的 USER 作用域 + use_count++。
     *
     * @param current 当前登录用户
     * @param id      市场模板ID
     */
    void useFromMarketplace(LoginUser current, Long id);
}
