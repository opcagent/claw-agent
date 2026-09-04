package com.claw.agent;

import com.claw.agent.config.tool.BuiltinToolFactory;
import com.claw.agent.config.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * claw-agent 启动类。
 * <p>
 * 基于 AgentScope Java 2.0 满血版的个人 Agent 平台：
 * <ul>
 *   <li>Harness 工程化：工作区 / 分层记忆 / 上下文压缩 / 子 Agent / 技能自学习 / Plan Mode</li>
 *   <li>权限系统 + HITL：工具调用三态决策（允许/审批/拒绝），人工确认一等公民</li>
 *   <li>多租户隔离：单实例服务所有用户，按 (userId, sessionId) 隔离状态</li>
 *   <li>事件流式：streamEvents 驱动前端 SSE 实时渲染</li>
 * </ul>
 *
 * @author claw
 */
@EnableScheduling // 开启定时任务（AgentScope 后台记忆维护 / 技能整理依赖调度器）
@Slf4j
@SpringBootApplication(exclude = {
        RedisAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class,
        // Quartz Scheduler 由 QuartzSchedulerConfig 手动管理（RAMJobStore + 自定义 Job 注册），
        // 排除 Spring Boot 自动配置避免 Bean 名称冲突
        QuartzAutoConfiguration.class
})
public class ClawAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClawAgentApplication.class, args);
    }

    /**
     * 应用启动后初始化工具注册器。
     *
     * @param toolRegistry 工具注册器
     * @param builtinToolFactory 内置工具工厂
     * @return CommandLineRunner
     */
    @Bean
    public CommandLineRunner initToolRegistry(ToolRegistry toolRegistry, BuiltinToolFactory builtinToolFactory) {
        return args -> {
            // 1. 注册内置工具工厂（支持 MultiSearchTools 等需要特殊参数的工具）
            toolRegistry.registerFactory(builtinToolFactory);
            log.info("已注册 BuiltinToolFactory");
            
            // 2. 初始化工具扫描与注册
            toolRegistry.initialize();
        };
    }
}
