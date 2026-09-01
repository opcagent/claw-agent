package com.claw.agent.config.infra;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Redis 可选配置：启动时自动探测 Redis 可用性，连接失败则跳过 Redis 初始化。
 * <p>
 * 当 {@code claw.redis.enabled} 不为 {@code false} 时激活（默认 {@code auto} 自动探测）；
 * 显式设为 {@code false} 则整个 Bean 不加载，AgentRegistry 直接走 JSON 存储。
 * <p>
 * 探测逻辑：尝试建立连接并执行 PING，失败则记录 WARN 日志并标记 {@link #redisAvailable} 为 false，
 * AgentRegistry 据此降级到 {@code JsonFileAgentStateStore}。
 */
@Slf4j
@Configuration
@ConditionalOnExpression("'${claw.redis.enabled:auto}' != 'false'")
@EnableConfigurationProperties(RedisProperties.class)
public class RedisOptionalConfig {

    /**
     * Redis 可用性探测结果：启动时尝试连接 Redis，成功返回 true，失败返回 false。
     * AgentRegistry 通过注入此 Bean 判断是否使用 Redis 状态存储。
     */
    @Bean
    public Boolean redisAvailable(RedisProperties redisProperties, ClawProperties clawProperties) {
        String mode = clawProperties.getRedis().getEnabled();
        // 显式禁用不应走到这里（ConditionalOnProperty 已拦截），防御性检查
        if ("false".equalsIgnoreCase(mode)) {
            log.info("Redis 已显式禁用（claw.redis.enabled=false），使用本地 JSON 存储");
            return false;
        }
        // auto 模式或 true：尝试连接
        return probeRedis(redisProperties);
    }

    /**
     * 尝试连接 Redis 并执行 PING，返回是否可用。
     */
    private boolean probeRedis(RedisProperties props) {
        RedisURI.Builder uriBuilder = RedisURI.builder()
                .withHost(props.getHost())
                .withPort(props.getPort())
                .withDatabase(props.getDatabase())
                .withTimeout(Duration.ofSeconds(3));
        if (StringUtils.hasText(props.getPassword())) {
            uriBuilder.withPassword(props.getPassword().toCharArray());
        }
        RedisClient client = null;
        try {
            client = RedisClient.create(uriBuilder.build());
            var connection = client.connect();
            String pong = connection.sync().ping();
            connection.close();
            if ("PONG".equalsIgnoreCase(pong)) {
                log.info("Redis 探测成功: {}:{}, database={}", props.getHost(), props.getPort(), props.getDatabase());
                return true;
            }
        } catch (Exception e) {
            log.warn("Redis 连接失败: {}:{} — 将降级为本地 JSON 文件存储。原因: {}",
                    props.getHost(), props.getPort(), e.getMessage());
        } finally {
            if (client != null) {
                client.shutdown();
            }
        }
        return false;
    }
}
