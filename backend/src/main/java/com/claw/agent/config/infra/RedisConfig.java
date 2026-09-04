package com.claw.agent.config.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Redis 基础设施配置：当 Redis 可用时提供 StringRedisTemplate + Pub/Sub 支持。
 * <p>
 * 多实例部署核心依赖：
 * <ul>
 *   <li>StringRedisTemplate：HITL 待确认 / 在线用户追踪 / 缓存失效广播</li>
 *   <li>RedisMessageListenerContainer：配置变更 Pub/Sub 订阅</li>
 * </ul>
 * 与 {@link RedisOptionalConfig} 互补：后者负责启动探测，本配置在探测通过后提供 Spring Data Redis Bean。
 */
@Slf4j
@Configuration
public class RedisConfig {

    /**
     * Lettuce 连接工厂：基于 Spring RedisProperties 构建，供 StringRedisTemplate 使用。
     * <p>
     * 仅在 redisAvailable=true 时创建（ConditionalOnBean），避免 Redis 不可用时启动报错。
     */
    @Bean
    @ConditionalOnBean(name = "redisAvailable")
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
    @ConditionalOnBean(LettuceConnectionFactory.class)
    public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    /**
     * Redis Pub/Sub 消息监听容器：配置变更 / 缓存失效广播的订阅基础设施。
     */
    @Bean
    @ConditionalOnBean(LettuceConnectionFactory.class)
    public RedisMessageListenerContainer redisMessageListenerContainer(LettuceConnectionFactory factory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        return container;
    }
}
