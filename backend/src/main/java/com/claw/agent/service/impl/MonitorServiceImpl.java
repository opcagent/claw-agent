package com.claw.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.claw.agent.mapper.TenantMapper;
import com.claw.agent.mapper.UserMapper;
import com.claw.agent.model.Tenant;
import com.claw.agent.model.User;
import com.claw.agent.model.dto.OnlineUserVO;
import com.claw.agent.security.LoginUser;
import com.claw.agent.service.MonitorService;
import com.claw.agent.service.OnlineUserTracker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 在线用户监控服务实现。
 * <p>
 * 快照来自 {@link OnlineUserTracker}（JwtAuthFilter 逐请求刷新），
 * 本实现批量补全昵称与租户名（避免逐条查询），并按查看者角色做租户级过滤：
 * 平台管理员全量、租户管理员仅本租户。
 */
@Service
@RequiredArgsConstructor
public class MonitorServiceImpl implements MonitorService {

    private final OnlineUserTracker onlineUserTracker;
    private final UserMapper userMapper;
    private final TenantMapper tenantMapper;

    /** 在线用户列表最大条数（防大规模场景下内存压力） */
    private static final int MAX_ONLINE_USERS = 500;

    @Override
    public List<OnlineUserVO> listOnlineUsers(LoginUser viewer) {
        // 租户管理员只看本租户（与日志等管理接口的可见性一致）；快照无租户信息时不纳入租户视图
        List<OnlineUserTracker.Snapshot> snapshots = onlineUserTracker.listActive().stream()
                .filter(s -> viewer.isAdmin() || Objects.equals(s.getTenantId(), viewer.getTenantId()))
                .limit(MAX_ONLINE_USERS)
                .toList();
        if (snapshots.isEmpty()) {
            return List.of();
        }
        // 批量补全昵称与租户名：一次性 IN 查询，快照数上限受保留窗口约束，规模可控
        Set<String> usernames = snapshots.stream().map(OnlineUserTracker.Snapshot::getUsername).collect(Collectors.toSet());
        Map<String, User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                        .in(User::getUsername, usernames)).stream()
                .collect(Collectors.toMap(User::getUsername, Function.identity(), (a, b) -> a));
        Set<Long> tenantIds = snapshots.stream().map(OnlineUserTracker.Snapshot::getTenantId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> tenantNames = tenantIds.isEmpty() ? Map.of()
                : tenantMapper.selectByIds(tenantIds).stream()
                .collect(Collectors.toMap(Tenant::getId, Tenant::getTenantName, (a, b) -> a));
        LocalDateTime onlineThreshold = LocalDateTime.now().minusMinutes(OnlineUserTracker.ONLINE_MINUTES);
        return snapshots.stream().map(s -> OnlineUserVO.builder()
                .userId(s.getUserId())
                .username(s.getUsername())
                .nickname(users.containsKey(s.getUsername()) ? users.get(s.getUsername()).getNickname() : null)
                .tenantId(s.getTenantId())
                .tenantName(s.getTenantId() == null ? null : tenantNames.get(s.getTenantId()))
                .lastActiveTime(s.getLastActiveTime())
                .lastIp(s.getLastIp())
                .online(!s.getLastActiveTime().isBefore(onlineThreshold))
                .build()).toList();
    }
}
