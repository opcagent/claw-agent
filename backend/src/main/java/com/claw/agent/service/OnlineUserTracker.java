package com.claw.agent.service;

import lombok.Getter;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户在线状态采集器（内存型）。
 * <p>
 * 由 {@code JwtAuthFilter} 在每次 token 校验通过后调用 {@link #touch} 刷新活跃快照，
 * 管理端监控页经 {@code MonitorService} 读取。以「最近活跃时间」近似在线状态：
 * JWT 无状态无法感知主动登出，心跳式的请求活跃时间是无侵入的最佳近似。
 * <p>
 * 限制：内存态仅对单实例有效，多实例部署需切换为 Redis 存储（键与 TTL 语义一致）；
 * 条目只保留 {@link #RETENTION_MINUTES} 内的活跃用户，查询时惰性清理，无需后台线程。
 */
@Component
public class OnlineUserTracker {

    /** 在线判定阈值：最近 5 分钟内有请求视为在线 */
    public static final int ONLINE_MINUTES = 5;

    /** 快照保留窗口：超过 30 分钟无活动的用户从内存移除 */
    public static final int RETENTION_MINUTES = 30;

    /** 用户名 → 活跃快照（用户名为业务唯一键，同一账号多端共用一条记录） */
    private final Map<String, Snapshot> snapshots = new ConcurrentHashMap<>();

    /**
     * 刷新用户活跃快照（每次认证通过的请求调用一次）。
     *
     * @param userId   用户 ID（旧版 token 可能为空）
     * @param username 用户名
     * @param tenantId 租户 ID
     * @param ip       客户端 IP（可为空）
     */
    public void touch(String userId, String username, Long tenantId, String ip) {
        if (username == null) {
            return;
        }
        snapshots.compute(username, (k, old) -> {
            Snapshot s = old != null ? old : new Snapshot();
            s.userId = userId;
            s.username = username;
            s.tenantId = tenantId;
            s.lastActiveTime = LocalDateTime.now();
            if (ip != null) {
                s.lastIp = ip;
            }
            return s;
        });
    }

    /**
     * 列出保留窗口内的活跃用户快照（按最近活跃时间倒序），并惰性清理过期条目。
     *
     * @return 快照列表（不含昵称等展示字段，由服务层补全）
     */
    public List<Snapshot> listActive() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(RETENTION_MINUTES);
        Iterator<Map.Entry<String, Snapshot>> it = snapshots.entrySet().iterator();
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

    /** 活跃快照（可变内部对象，仅在采集器内共享，读取方不应修改） */
    @Getter
    public static class Snapshot {
        private String userId;
        private String username;
        private Long tenantId;
        private LocalDateTime lastActiveTime;
        private String lastIp;
    }
}
