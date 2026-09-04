package com.claw.agent.config.infra;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 自定义条件：Redis 探测成功时匹配。
 * <p>
 * 核心设计：Condition 自身从 {@link org.springframework.core.env.Environment} 读取 Redis 配置并执行探测，
 * 不依赖其他 Bean 的实例化（{@code @Bean} 方法上的 {@code @Conditional} 在 REGISTER_BEAN 阶段求值，
 * 此时同配置类内的其他 {@code @Bean} 尚未执行，无法通过 {@code getBean()} 获取探测结果）。
 * <p>
 * 探测结果缓存在静态字段 {@link #CACHED_AVAILABLE}，保证整个启动周期只探测一次；
 * {@link RedisOptionalConfig#redisAvailable} 复用同一缓存，避免重复连接。
 */
@Slf4j
public class RedisAvailableCondition implements ConfigurationCondition {

    /** 探测结果缓存（null = 尚未探测） */
    private static volatile Boolean CACHED_AVAILABLE;

    @Override
    public ConfigurationPhase getConfigurationPhase() {
        return ConfigurationPhase.REGISTER_BEAN;
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return probeOrCache(context);
    }

    /**
     * 执行 Redis 探测并缓存结果。
     * <p>
     * 优先读取 {@code claw.redis.enabled}：显式 {@code false} 直接返回 false；
     * 否则从 Environment 取 Redis 连接参数执行 PING 探测。
     *
     * @param context Spring 条件上下文
     * @return Redis 是否可用
     */
    static boolean probeOrCache(ConditionContext context) {
        if (CACHED_AVAILABLE != null) {
            return CACHED_AVAILABLE;
        }
        // 检查 claw.redis.enabled 显式禁用
        String mode = context.getEnvironment().getProperty("claw.redis.enabled", "auto");
        if ("false".equalsIgnoreCase(mode)) {
            CACHED_AVAILABLE = false;
            return false;
        }
        // 从 Environment 读取 Redis 连接参数
        String host = context.getEnvironment().getProperty("spring.data.redis.host",
                context.getEnvironment().getProperty("spring.redis.host", "localhost"));
        int port = Integer.parseInt(context.getEnvironment().getProperty("spring.data.redis.port",
                context.getEnvironment().getProperty("spring.redis.port", "6379")));
        int database = Integer.parseInt(context.getEnvironment().getProperty("spring.data.redis.database",
                context.getEnvironment().getProperty("spring.redis.database", "0")));
        String password = context.getEnvironment().getProperty("spring.data.redis.password",
                context.getEnvironment().getProperty("spring.redis.password"));

        CACHED_AVAILABLE = doProbe(host, port, database, password);
        return CACHED_AVAILABLE;
    }

    /**
     * 供 {@link RedisOptionalConfig#redisAvailable} 复用：返回已缓存的探测结果。
     * <p>
     * 必须在 Condition 求值之后调用（正常情况下 Condition 先于 Bean 实例化执行，
     * 此时缓存已由 {@link #probeOrCache} 写入）。
     *
     * @return 已缓存的探测结果，未探测时返回 false
     */
    static boolean cachedResult() {
        return Boolean.TRUE.equals(CACHED_AVAILABLE);
    }

    /**
     * 实际执行 Redis PING 探测。
     */
    private static boolean doProbe(String host, int port, int database, String password) {
        RedisURI.Builder uriBuilder = RedisURI.builder()
                .withHost(host)
                .withPort(port)
                .withDatabase(database)
                .withTimeout(Duration.ofSeconds(3));
        if (StringUtils.hasText(password)) {
            uriBuilder.withPassword(password.toCharArray());
        }
        RedisClient client = null;
        try {
            client = RedisClient.create(uriBuilder.build());
            var connection = client.connect();
            String pong = connection.sync().ping();
            connection.close();
            if ("PONG".equalsIgnoreCase(pong)) {
                log.info("Redis 探测成功: {}:{}, database={}", host, port, database);
                return true;
            }
        } catch (Exception e) {
            log.warn("Redis 连接失败: {}:{} — 将降级为本地 JSON 文件存储。原因: {}",
                    host, port, e.getMessage());
        } finally {
            if (client != null) {
                client.shutdown();
            }
        }
        return false;
    }
}
