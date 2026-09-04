package com.claw.agent.config.infra;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.util.StringUtils;

/**
 * Redis 可选配置（统一入口）：启动时探测 Redis 可用性，可用则创建全部基础设施 Bean。
 * <p>
 * 当 {@code claw.redis.enabled} 不为 {@code false} 时激活（默认 {@code auto} 自动探测）；
 * 显式设为 {@code false} 则整个配置类不加载，所有组件降级为内存模式。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>{@link RedisAvailableCondition} 在 REGISTER_BEAN 阶段自行从 Environment 读取配置并探测，
 *       结果缓存在静态字段，不依赖任何 Bean 实例化</li>
 *   <li>{@link #redisAvailable} 复用 Condition 缓存的探测结果，避免重复连接</li>
 *   <li>基础设施 Bean（连接工厂/Template/监听容器）使用 {@link RedisAvailableCondition}
 *       控制创建</li>
 * </ul>
 */
@Slf4j
@Configuration
@ConditionalOnExpression("'${claw.redis.enabled:auto}' != 'false'")
@EnableConfigurationProperties(RedisProperties.class)
public class RedisOptionalConfig {

    /**
     * Redis 可用性探测结果：复用 {@link RedisAvailableCondition} 已缓存的探测结果。
     * <p>
     * Condition 在 REGISTER_BEAN 阶段先于本 Bean 实例化执行，缓存已写入；
     * AgentRegistry 通过注入此 Bean 判断是否使用 Redis 状态存储。
     */
    @Bean
    public Boolean redisAvailable() {
        return RedisAvailableCondition.cachedResult();
    }

    /**
     * Lettuce 连接工厂：基于 Spring RedisProperties 构建，供 StringRedisTemplate 使用。
     */
    @Bean
    @Conditional(RedisAvailableCondition.class)
    public LettuceConnectionFactory lettuceConnectionFactory(RedisProperties redisProperties) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisProperties.getHost());
        config.setPort(redisProperties.getPort());
        config.setDatabase(redisProperties.getDatabase());
        if (StringUtils.hasText(redisProperties.getPassword())) {
            config.setPassword(redisProperties.getPassword());
        }
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();
        log.info("Lettuce 连接工厂已初始化: {}:{}/{}", redisProperties.getHost(), redisProperties.getPort(), redisProperties.getDatabase());
        return factory;
    }

    /**
     * StringRedisTemplate：多实例共享状态的核心操作入口。
     */
    @Bean
    @Conditional(RedisAvailableCondition.class)
    public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    /**
     * Redis Pub/Sub 消息监听容器：配置变更 / 缓存失效广播的订阅基础设施。
     */
    @Bean
    @Conditional(RedisAvailableCondition.class)
    public RedisMessageListenerContainer redisMessageListenerContainer(LettuceConnectionFactory factory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        return container;
    }
}
