package com.claw.agent.config.infra;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.ToolUseBlock;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HITL 待确认工具调用存储：多实例部署时通过 Redis Hash 共享待确认状态。
 * <p>
 * Redis 可用时所有实例共享同一份待确认数据，负载均衡将确认请求打到任意节点均可恢复执行；
 * Redis 不可用时降级为内存 ConcurrentHashMap，仅支持单实例（与改造前行为一致）。
 * <p>
 * Redis 存储结构：Hash {@code claw-agent:hitl:pending}，field = username|sessionId，value = JSON(ToolUseBlock 列表)。
 * 键 TTL = {@value #TTL_HOURS} 小时，每次写入刷新，防止 abandoned HITL 条目永久残留。
 */
@Slf4j
@Component
public class HitlPendingStore {

    /** Redis Hash 键名 */
    private static final String REDIS_KEY = "claw-agent:hitl:pending";

    /** 待确认条目过期时间（小时） */
    private static final int TTL_HOURS = 2;

    /** 键分隔符（username 与 sessionId 之间） */
    private static final String KEY_SEP = "|";

    /**
     * Lua 脚本：原子性「取出并删除」Hash 中的指定 field。
     * <p>
     * 多实例部署时防止竞态：两个节点同时 get+delete 导致同一份 HITL 确认被处理两次。
     * Lua 脚本在 Redis 单线程内原子执行，保证只有一个节点拿到值。
     */
    private static final String LUA_HGET_AND_DELETE =
            "local v = redis.call('HGET', KEYS[1], ARGV[1])\n" +
            "if v then redis.call('HDEL', KEYS[1], ARGV[1]) end\n" +
            "return v";

    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    /** Redis 不可用时的内存降级 */
    private final Map<String, List<ToolUseBlock>> localFallback = new ConcurrentHashMap<>();

    public HitlPendingStore(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        if (redisTemplate != null) {
            log.info("HITL 待确认存储: Redis（多实例共享）");
        } else {
            log.info("HITL 待确认存储: 内存（单实例模式，多实例部署需启用 Redis）");
        }
    }

    /**
     * 缓存待确认工具调用。
     *
     * @param username  用户名
     * @param sessionId 会话ID
     * @param toolCalls 待确认的工具调用列表
     */
    public void put(String username, String sessionId, List<ToolUseBlock> toolCalls) {
        String key = buildKey(username, sessionId);
        if (redisTemplate != null) {
            try {
                // 反序列化需要 ContentBlock 多态类型鉴别器 "type"，
                // 但 Jackson 序列化 final 子类 ToolUseBlock 时不会自动写入 type 属性，
                // 因此用 writerFor(List<ContentBlock>) 强制走基类多态序列化路径
                String json = objectMapper.writerFor(new TypeReference<List<ContentBlock>>() {})
                        .writeValueAsString(toolCalls);
                redisTemplate.opsForHash().put(REDIS_KEY, key, json);
                // 每次写入刷新 TTL，防止 abandoned HITL 条目永久残留
                redisTemplate.expire(REDIS_KEY, Duration.ofHours(TTL_HOURS));
                return;
            } catch (JsonProcessingException e) {
                log.warn("序列化 HITL 待确认数据失败，降级为内存存储", e);
            }
        }
        localFallback.put(key, toolCalls);
    }

    /**
     * 获取并移除待确认工具调用（Lua 脚本原子性取出）。
     * <p>
     * 多实例部署时，负载均衡可能将同一用户的两次确认请求打到不同节点。
     * 使用 Lua 脚本保证 HGET+HDEL 在 Redis 单线程内原子执行，
     * 只有一个节点能取到值，其余节点返回 null 走「无待确认」分支。
     *
     * @param username  用户名
     * @param sessionId 会话ID
     * @return 待确认的工具调用列表，不存在返回 null
     */
    public List<ToolUseBlock> remove(String username, String sessionId) {
        String key = buildKey(username, sessionId);
        if (redisTemplate != null) {
            try {
                // Lua 原子取出：HGET + HDEL 在单次 eval 内完成，避免多节点竞态
                var script = new org.springframework.data.redis.core.script.DefaultRedisScript<String>(
                        LUA_HGET_AND_DELETE, String.class);
                String val = redisTemplate.execute(script, List.of(REDIS_KEY), key);
                if (val == null) return null;
                return objectMapper.readValue(val, new TypeReference<>() {});
            } catch (Exception e) {
                log.warn("Redis HITL 读取失败，降级为内存存储", e);
            }
        }
        return localFallback.remove(key);
    }

    /**
     * 检查是否存在待确认条目（不取出）。
     *
     * @param username  用户名
     * @param sessionId 会话ID
     * @return 是否存在
     */
    public boolean hasPending(String username, String sessionId) {
        String key = buildKey(username, sessionId);
        if (redisTemplate != null) {
            try {
                return Boolean.TRUE.equals(redisTemplate.opsForHash().hasKey(REDIS_KEY, key));
            } catch (Exception e) {
                log.warn("Redis HITL 查询失败，降级为内存查询", e);
            }
        }
        List<ToolUseBlock> list = localFallback.get(key);
        return list != null && !list.isEmpty();
    }

    /**
     * 删除待确认条目（会话删除时清理残留）。
     *
     * @param username  用户名
     * @param sessionId 会话ID
     */
    public void delete(String username, String sessionId) {
        String key = buildKey(username, sessionId);
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForHash().delete(REDIS_KEY, key);
                return;
            } catch (Exception e) {
                log.warn("Redis HITL 删除失败", e);
            }
        }
        localFallback.remove(key);
    }

    /** 构建存储键 */
    private String buildKey(String username, String sessionId) {
        return username + KEY_SEP + sessionId;
    }
}
