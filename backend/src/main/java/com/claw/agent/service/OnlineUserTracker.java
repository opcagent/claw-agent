package com.claw.agent.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户在线状态采集器（支持 Redis 多实例 / 内存单实例双模式）。
 * <p>
 * 由 {@code JwtAuthFilter} 在每次 token 校验通过后调用 {@link #touch} 刷新活跃快照，
 * 管理端监控页经 {@code MonitorService} 读取。以「最近活跃时间」近似在线状态：
 * JWT 无状态无法感知主动登出，心跳式的请求活跃时间是无侵入的最佳近似。
 * <p>
 * Redis 模式：每用户一个键 {@code claw-agent:online:{username}}，TTL = {@value #RETENTION_MINUTES} 分钟，
 * 由 Redis 自动过期清理，无需后台线程。所有实例共享同一份在线数据。
 * <p>
 * 内存模式：Redis 不可用时降级为 ConcurrentHashMap，惰性清理过期条目（与原实现一致）。
 */
@Slf4j
@Component
public class OnlineUserTracker {

    /** 在线判定阈值：最近 5 分钟内有请求视为在线 */
    public static final int ONLINE_MINUTES = 5;

    /** 快照保留窗口：超过 30 分钟无活动的用户自动清除 */
    public static final int RETENTION_MINUTES = 30;

    /** Redis 键前缀 */
    private static final String REDIS_KEY_PREFIX = "claw-agent:online:";

    /** 时间戳序列化格式 */
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    /** Redis 不可用时的内存降级 */
    private final Map<String, Snapshot> localSnapshots = new ConcurrentHashMap<>();

    /**
     * 刷新用户活跃快照（每次认证通过的请求调用一次）。
     * <p>
     * Redis 模式：写入 Hash 字段并刷新键 TTL（{@value #RETENTION_MINUTES} 分钟后自动过期）；
     * 内存模式：更新 ConcurrentHashMap 中的快照。
     *
     * @param userId   用户 ID（旧版 token 可能为空）
     * @param username 用户名
     * @param tenantId 租户 ID
     * @param ip       客户端 IP（可为空）
     */
    public void touch(String userId, String username, Long tenantId, String ip) {
        if (username == null) return;
        if (redisTemplate != null) {
            try {
                String key = redisKey(username);
                String time = LocalDateTime.now().format(DT_FMT);
                String value = String.join("|",
                        nullSafe(userId), nullSafe(tenantId), nullSafe(ip), time);
                redisTemplate.opsForValue().set(key, value, Duration.ofMinutes(RETENTION_MINUTES));
                return;
            } catch (Exception e) {
                log.warn("Redis 在线追踪写入失败，降级为内存", e);
            }
        }
        localSnapshots.compute(username, (k, old) -> {
            Snapshot s = old != null ? old : new Snapshot();
            s.userId = userId;
            s.username = username;
            s.tenantId = tenantId;
            s.lastActiveTime = LocalDateTime.now();
            if (ip != null) s.lastIp = ip;
            return s;
        });
    }

    /**
     * 列出活跃用户快照（按最近活跃时间倒序）。
     * <p>
     * Redis 模式：使用 SCAN（非 KEYS）增量遍历前缀匹配的键，不阻塞 Redis 事件循环；
     * 内存模式：惰性清理超过 {@value #RETENTION_MINUTES} 分钟的条目。
     *
     * @return 快照列表（不含昵称等展示字段，由服务层补全）
     */
    public List<Snapshot> listActive() {
        if (redisTemplate != null) {
            try {
                List<Snapshot> result = new ArrayList<>();
                // SCAN 替代 KEYS：增量游标遍历，不阻塞 Redis 主线程（生产安全）
                ScanOptions options = ScanOptions.scanOptions()
                        .match(REDIS_KEY_PREFIX + "*").count(200).build();
                try (Cursor<String> cursor = redisTemplate.scan(options)) {
                    while (cursor.hasNext()) {
                        String key = cursor.next();
                        String value = redisTemplate.opsForValue().get(key);
                        if (value == null) continue;
                        String[] parts = value.split("\\|", -1);
                        if (parts.length < 4) continue;
                        Snapshot s = new Snapshot();
                        s.userId = emptyToNull(parts[0]);
                        s.username = key.substring(REDIS_KEY_PREFIX.length());
                        s.tenantId = parts[1].isEmpty() ? null : Long.parseLong(parts[1]);
                        s.lastIp = emptyToNull(parts[2]);
                        s.lastActiveTime = LocalDateTime.parse(parts[3], DT_FMT);
                        result.add(s);
                    }
                }
                result.sort(Comparator.comparing(Snapshot::getLastActiveTime).reversed());
                return result;
            } catch (Exception e) {
                log.warn("Redis 在线追踪读取失败，降级为内存", e);
            }
        }
        // 内存降级：惰性清理过期条目
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(RETENTION_MINUTES);
        var it = localSnapshots.entrySet().iterator();
        List<Snapshot> result = new ArrayList<>();
        while (it.hasNext()) {
            Snapshot s = it.next().getValue();
            if (s.lastActiveTime.isBefore(threshold)) {
                it.remove();
            } else {
                result.add(s);
            }
        }
        result.sort(Comparator.comparing(Snapshot::getLastActiveTime).reversed());
        return result;
    }

    /** 构建 Redis 键 */
    private String redisKey(String username) {
        return REDIS_KEY_PREFIX + username;
    }

    /** null 安全转字符串（null → 空串，避免 split 错位） */
    private String nullSafe(Object obj) {
        return obj == null ? "" : obj.toString();
    }

    /** 空串转 null */
    private String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    /**
     * 活跃快照。
     * <p>
     * Redis 模式下通过 {@code @Data} 的 setter 反序列化；内存模式下直接字段赋值。
     * 仅在采集器内部使用，外部通过 getter 读取。
     */
    @Data
    public static class Snapshot {
        private String userId;
        private String username;
        private Long tenantId;
        private LocalDateTime lastActiveTime;
        private String lastIp;
    }
}
