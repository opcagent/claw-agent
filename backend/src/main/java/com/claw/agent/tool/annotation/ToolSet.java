package com.claw.agent.tool.annotation;

import java.lang.annotation.*;

/**
 * 工具集元数据注解。
 * <p>
 * 用于标记工具类,提供工具的元信息,支持自动扫描和注册。
 * 示例:
 * <pre>{@code
 * @ToolSet(
 *     code = "system_tools",
 *     name = "系统工具",
 *     description = "提供时间查询、日期计算、UUID 生成等系统级功能",
 *     category = "utility",
 *     enabledByDefault = true,
 *     version = "1.0.0"
 * )
 * public class SystemTools { ... }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ToolSet {

    /**
     * 工具集唯一标识符(用于数据库配置和权限控制)。
     * <p>
     * 命名规范: 小写字母+下划线,如 {@code system_tools}、{@code math_tools}
     */
    String code();

    /**
     * 工具集显示名称(用于前端展示)。
     */
    String name();

    /**
     * 工具集描述(用于前端提示和管理后台)。
     */
    String description() default "";

    /**
     * 工具分类(用于分组展示)。
     * <p>
     * 可选值: 
     * <ul>
     *   <li>utility - 实用工具（时间、计算、编码等）</li>
     *   <li>search - 搜索工具（联网搜索、知识库检索）</li>
     *   <li>data - 数据处理（文件读写、数据转换）</li>
     *   <li>code - 代码相关（语法检查、正则测试）</li>
     *   <li>ai - AI 增强（文本生成、图像识别）</li>
     *   <li>system - 系统管理（配置查询、日志查看）</li>
     * </ul>
     */
    String category() default "utility";

    /**
     * 是否默认启用。
     * <p>
     * true: 新用户自动启用此工具集<br>
     * false: 需要管理员手动启用
     */
    boolean enabledByDefault() default true;

    /**
     * 版本号(用于兼容性管理和更新提示)。
     */
    String version() default "1.0.0";

    /**
     * 依赖的其他工具集代码列表。
     * <p>
     * 如果此工具集依赖其他工具集,在此声明,启动时会检查依赖是否已启用。
     */
    String[] dependencies() default {};

    /**
     * 是否需要 HITL 审批。
     * <p>
     * true: 调用此工具集中的任何工具都需要人工确认<br>
     * false: 直接执行(受权限系统控制)
     */
    boolean requiresHITL() default false;

    /**
     * 适用角色列表(为空表示所有角色可用)。
     * <p>
     * 示例: {"ADMIN", "DATA_ANALYST"}
     */
    String[] allowedRoles() default {};
}
