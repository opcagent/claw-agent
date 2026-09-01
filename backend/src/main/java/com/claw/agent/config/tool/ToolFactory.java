package com.claw.agent.config.tool;

import com.claw.agent.tool.annotation.ToolSet;

/**
 * 工具工厂接口。
 * <p>
 * 用于创建需要特殊参数的工具实例,解决统一注册与个性化初始化的矛盾。
 * 实现此接口的工具类会在 ToolRegistry 中通过 factory 方法实例化,而非直接反射构造。
 * 
 * @param <T> 工具类型
 */
public interface ToolFactory<T> {

    /**
     * 创建工具实例。
     *
     * @param metadata 工具集元数据
     * @return 工具实例
     */
    T create(ToolRegistry.ToolMetadata metadata);

    /**
     * 是否支持指定的工具集。
     *
     * @param code 工具集代码
     * @return true 表示支持
     */
    boolean supports(String code);
}
