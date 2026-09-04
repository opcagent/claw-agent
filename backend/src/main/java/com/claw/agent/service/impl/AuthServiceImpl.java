package com.claw.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.claw.agent.common.*;
import com.claw.agent.config.infra.TraceFilter;
import com.claw.agent.mapper.*;
import com.claw.agent.model.*;
import com.claw.agent.model.dto.*;
import com.claw.agent.security.JwtUtil;
import com.claw.agent.security.LoginRateLimiter;
import com.claw.agent.security.LoginUser;
import com.claw.agent.service.AuthService;
import com.claw.agent.service.MenuService;
import com.claw.agent.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证业务实现：登录 / 修改密码 / 登出 / 当前用户信息。
 * <p>
 * RBAC：登录时经 sys_user_tenant + sys_role 聚合角色键写入 JWT，
 * 权限标识（perms）经三级联表聚合后随登录响应下发前端；
 * 登录成功失败与登出事件统一写入 {@code sys_login_log}。
 * 账号由管理员在用户管理中创建，不提供自助注册。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final MenuMapper menuMapper;
    private final MenuService menuService;
    private final LoginLogMapper loginLogMapper;
    private final TenantMapper tenantMapper;
    private final UserTenantMapper userTenantMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final LoginRateLimiter loginRateLimiter;

    @Override
    public LoginResponse login(LoginRequest request) {
        // 防爆破：失败计数达阈后锁定该「用户名+IP」维度，锁定期间不区分密码对错直接拒绝；
        // IP 由控制器桥接到 IpContextHolder（登录为匿名接口不走 ReactiveSupport）
        String clientIp = IpContextHolder.getIp();
        loginRateLimiter.check(request.getUsername(), clientIp);
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            loginRateLimiter.recordFailure(request.getUsername(), clientIp);
            recordLoginLog(request.getUsername(), null, LoginLog.TYPE_LOGIN, 0, "用户名或密码错误");
            throw new BizException(ResultCode.LOGIN_FAILED);
        }
        if (!user.isEnabled()) {
            recordLoginLog(user.getUsername(), null, LoginLog.TYPE_LOGIN, 0, "账号已禁用");
            throw new BizException(ResultCode.USER_DISABLED);
        }

        // 平台管理员特殊处理：不属于任何组织，直接签发 JWT（admin 角色 + 全权限）
        if (RoleConstants.PLATFORM_ADMIN_USERNAME.equals(user.getUsername())) {
            LoginResponse response = buildPlatformAdminLoginResponse(user);
            loginRateLimiter.clear(user.getUsername(), clientIp);
            recordLoginLog(user.getUsername(), null, LoginLog.TYPE_LOGIN, 1, "平台管理员登录成功");
            log.info("平台管理员登录: {}", user.getUsername());
            return response;
        }

        // 多组织兼容：查询用户加入的所有组织（同一组织可能有多个角色记录）
        List<UserTenant> userTenants = userTenantMapper.selectList(new LambdaQueryWrapper<UserTenant>()
                .eq(UserTenant::getUserId, user.getId())
                .eq(UserTenant::getStatus, 1));

        // 按 tenantId 分组去重：同一组织多角色只算一个组织
        Map<Long, List<UserTenant>> tenantGroups = new LinkedHashMap<>();
        for (UserTenant ut : userTenants) {
            tenantGroups.computeIfAbsent(ut.getTenantId(), k -> new ArrayList<>()).add(ut);
        }

        // 确定当前活跃组织：多组织时取 is_default=1 的默认组织，无默认则取第一个
        Long activeTenantId;
        if (tenantGroups.size() > 1) {
            activeTenantId = tenantGroups.entrySet().stream()
                    .filter(e -> e.getValue().stream().anyMatch(ut -> ut.getIsDefault() != null && ut.getIsDefault() == 1))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(tenantGroups.keySet().iterator().next());
            log.info("用户属于多个组织，自动选择默认组织: user={}, tenant={}", user.getUsername(), activeTenantId);
        } else {
            // 单组织：直接选唯一组织；无记录说明用户未分配任何组织，拒绝登录
            if (tenantGroups.isEmpty()) {
                recordLoginLog(user.getUsername(), null, LoginLog.TYPE_LOGIN, 0, "用户未分配任何组织");
                throw new BizException(ResultCode.FORBIDDEN, "您尚未被分配到任何组织，请联系管理员");
            }
            activeTenantId = tenantGroups.keySet().iterator().next();
        }
        LoginResponse response = buildLoginResponse(user, activeTenantId);
        // 登录成功清零失败计数（同维度历史失败不再影响后续登录）
        loginRateLimiter.clear(user.getUsername(), clientIp);
        recordLoginLog(user.getUsername(), activeTenantId, LoginLog.TYPE_LOGIN, 1, "登录成功");
        log.info("用户登录成功: user={}, tenant={}", user.getUsername(), activeTenantId);
        return response;
    }

    @Override
    public void changePassword(LoginUser current, ChangePasswordRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, current.getUsername()).last("LIMIT 1"));
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BizException(ResultCode.PARAM_ERROR, "原密码不正确");
        }
        // 新密码与原密码相同视为无效操作，避免用户误以为修改成功
        if (request.getOldPassword().equals(request.getNewPassword())) {
            throw new BizException(ResultCode.PARAM_ERROR, "新密码不能与原密码相同");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(user);
        log.info("用户修改密码成功: {}", current.getUsername());
    }

    @Override
    public void logout(LoginUser current) {
        recordLoginLog(current.getUsername(), current.getTenantId(), LoginLog.TYPE_LOGOUT, 1, "登出成功");
        log.info("用户登出: {}", current.getUsername());
    }

    @Override
    public LoginResponse currentUserInfo(LoginUser current) {
        // 直接使用 JWT 中的 userId，避免冗余查库
        String userId = current.getUserId();
        // 账号被删除后 JWT 未到期仍可调用：空权限处理，避免 null 主键进联表查询；
        // 多角色场景下只返回当前活跃组织的权限，避免跨组织权限泄露
        List<String> permissions = current.isAdmin()
                ? List.of(RoleConstants.ALL_PERMISSIONS)
                : (userId == null ? List.of()
                        : menuMapper.selectPermsByUserIdAndTenantId(userId, current.getTenantId()));
        return LoginResponse.builder()
                .username(current.getUsername())
                .tenantId(current.getTenantId())
                .tenantName(selectTenantName(current.getTenantId()))
                .roles(current.getRoleKeys())
                .permissions(permissions)
                .build();
    }

    @Override
    public LoginResponse switchTenant(LoginUser current, SwitchTenantRequest request) {
        // 1. 校验用户属于目标组织
        UserTenant ut = userTenantMapper.selectOne(new LambdaQueryWrapper<UserTenant>()
                .eq(UserTenant::getUserId, current.getUserId())
                .eq(UserTenant::getTenantId, request.getTenantId())
                .eq(UserTenant::getStatus, 1)
                .last("LIMIT 1"));
        if (ut == null) {
            throw new BizException(ResultCode.FORBIDDEN, "您不属于该组织或已被禁用");
        }

        // 2. 重新签发 JWT（tenant_id 已迁移到 sys_user_tenant，无需更新 sys_user）
        User user = userMapper.selectById(current.getUserId());
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        LoginResponse response = buildLoginResponse(user, request.getTenantId());
        log.info("用户切换组织: user={}, from={}, to={}",
                current.getUsername(), current.getTenantId(), request.getTenantId());
        return response;
    }

    @Override
    public List<TenantBrief> listMyTenants(LoginUser current) {
        // 平台管理员不属于任何组织，直接返回空列表
        if (current.isAdmin()) {
            return List.of();
        }
        // 直接使用 JWT 中的 userId，避免冗余查库
        String userId = current.getUserId();
        if (userId == null) {
            return List.of();
        }
        List<UserTenant> userTenants = userTenantMapper.selectList(new LambdaQueryWrapper<UserTenant>()
                .eq(UserTenant::getUserId, userId)
                .eq(UserTenant::getStatus, 1));
        // 按 tenantId 分组去重：同一组织多角色只生成一个 brief
        Map<Long, List<UserTenant>> tenantGroups = new LinkedHashMap<>();
        for (UserTenant ut : userTenants) {
            tenantGroups.computeIfAbsent(ut.getTenantId(), k -> new ArrayList<>()).add(ut);
        }
        return buildTenantBriefs(userId, tenantGroups);
    }

    /**
     * 构建指定组织下的登录响应（聚合角色/权限/签发 JWT）。
     *
     * @param user       用户实体
     * @param tenantId   目标组织ID
     * @return 完整登录响应
     */
    private LoginResponse buildLoginResponse(User user, Long tenantId) {
        // 查询用户在目标组织内的角色（角色按租户隔离）
        List<Role> roles = roleMapper.selectRolesByUserIdAndTenantId(user.getId(), tenantId);
        List<String> roleKeys = roles.stream().map(Role::getRoleKey).toList();
        List<String> permissions = roleKeys.contains(RoleConstants.ROLE_ADMIN)
                ? List.of(RoleConstants.ALL_PERMISSIONS)
                : menuMapper.selectPermsByUserIdAndTenantId(user.getId(), tenantId);
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), tenantId, roleKeys, permissions);
        return LoginResponse.builder()
                .token(token)
                .username(user.getUsername())
                .nickname(user.getNickname())
                .tenantId(tenantId)
                .tenantName(selectTenantName(tenantId))
                .roles(roleKeys)
                .permissions(permissions)
                .build();
    }

    /**
     * 构建平台管理员登录响应（不属于任何组织，直接授予 admin 角色 + 全权限）。
     *
     * @param user 平台管理员用户实体
     * @return 登录响应
     */
    private LoginResponse buildPlatformAdminLoginResponse(User user) {
        List<String> roleKeys = List.of(RoleConstants.ROLE_ADMIN);
        List<String> permissions = List.of(RoleConstants.ALL_PERMISSIONS);
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), 0L, roleKeys, permissions);
        return LoginResponse.builder()
                .token(token)
                .username(user.getUsername())
                .nickname(user.getNickname())
                .tenantId(0L)
                .tenantName("平台管理")
                .roles(roleKeys)
                .permissions(permissions)
                .build();
    }

    /**
     * 构建用户可登录的组织简要列表（按组织分组，聚合每个组织内的所有角色键）。
     * <p>同一用户在同一组织可能有多个角色（多角色场景），需聚合去重后只生成一个 brief。
     *
     * @param userId       用户ID
     * @param tenantGroups 按 tenantId 分组的组织关联记录
     * @return 组织简要列表（每个组织一条）
     */
    private List<TenantBrief> buildTenantBriefs(String userId, Map<Long, List<UserTenant>> tenantGroups) {
        // 批量查询所有租户（避免循环内逐条 selectById）
        List<Tenant> tenants = tenantMapper.selectBatchIds(tenantGroups.keySet());
        Map<Long, Tenant> tenantMap = tenants.stream()
                .collect(java.util.stream.Collectors.toMap(Tenant::getId, t -> t, (a, b) -> a));

        List<TenantBrief> briefs = new ArrayList<>();
        for (Map.Entry<Long, List<UserTenant>> entry : tenantGroups.entrySet()) {
            Long tenantId = entry.getKey();
            List<UserTenant> uts = entry.getValue();
            Tenant tenant = tenantMap.get(tenantId);
            // 租户不存在或已禁用则跳过：禁用租户不可登录也不可切换
            if (tenant == null || tenant.getStatus() == null || tenant.getStatus() != 1) {
                continue;
            }
            // 查询用户在该组织内的所有角色（按组织隔离）
            List<Role> roles = roleMapper.selectRolesByUserIdAndTenantId(userId, tenantId);
            List<String> roleKeys = roles.stream().map(Role::getRoleKey).toList();
            // isDefault 取该组织内任意一条记录（通常只有第一条为 1）
            boolean isDefault = uts.stream().anyMatch(u -> u.getIsDefault() != null && u.getIsDefault() == 1);
            briefs.add(TenantBrief.builder()
                    .tenantId(tenantId)
                    .tenantCode(tenant.getTenantCode())
                    .tenantName(tenant.getTenantName())
                    .roleKeys(roleKeys)
                    .isDefault(isDefault)
                    .build());
        }
        return briefs;
    }

    /**
     * 按租户 ID 查租户名称（展示用）。
     * <p>
     * 租户不存在或入参为空时返回 null，前端降级展示，不阻断认证主流程。
     *
     * @param tenantId 租户 ID，可为空
     * @return 租户名称，不存在时为空
     */
    private String selectTenantName(Long tenantId) {
        if (tenantId == null) {
            return null;
        }
        Tenant tenant = tenantMapper.selectById(tenantId);
        return tenant == null ? null : tenant.getTenantName();
    }

    /** 按用户名查主键（权限联表查询需要用户 ID） */
    private String selectUserId(String username) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username).last("LIMIT 1"));
        return user == null ? null : user.getId();
    }

    @Override
    public List<Menu> listMyMenus(LoginUser current) {
        // 平台管理员不受角色菜单授权约束（JWT 权限为 *:*:*），
        // 复用 MenuService 的 Caffeine 缓存，避免每次刷新页面都打 DB
        if (current.isAdmin()) {
            return menuService.listEnabledMenus().stream()
                    .filter(m -> Menu.TYPE_DIR.equals(m.getMenuType()) || Menu.TYPE_MENU.equals(m.getMenuType()))
                    .sorted((a, b) -> {
                        int cmp = Long.compare(a.getParentId() == null ? 0 : a.getParentId(),
                                b.getParentId() == null ? 0 : b.getParentId());
                        return cmp != 0 ? cmp : Integer.compare(
                                a.getOrderNum() == null ? 0 : a.getOrderNum(),
                                b.getOrderNum() == null ? 0 : b.getOrderNum());
                    })
                    .toList();
        }
        // 直接使用 JWT 中的 userId，避免冗余查库
        String userId = current.getUserId();
        if (userId == null) {
            return List.of();
        }
        // 多角色场景下只返回当前活跃组织的菜单，避免跨组织菜单泄露
        return menuMapper.selectMenusByUserIdAndTenantId(userId, current.getTenantId());
    }

    @Override
    public ProfileResponse profile(LoginUser current) {
        // 直接使用 JWT 中的 userId 查询用户信息，避免冗余查库
        User user = current.getUserId() != null ? userMapper.selectById(current.getUserId()) : null;
        // 角色名称与键同序下发，供个人中心展示中文角色名；
        // 多角色场景下只查当前活跃组织的角色，避免跨组织角色泄露
        List<Role> roles = user == null ? List.of()
                : roleMapper.selectRolesByUserIdAndTenantId(user.getId(), current.getTenantId());
        // 最近一次成功登录：失败记录不算「上次登录」，避免误导
        LoginLog last = loginLogMapper.selectOne(new LambdaQueryWrapper<LoginLog>()
                .eq(LoginLog::getUsername, current.getUsername())
                .eq(LoginLog::getEventType, LoginLog.TYPE_LOGIN)
                .eq(LoginLog::getStatus, 1)
                .orderByDesc(LoginLog::getLoginTime)
                .last("LIMIT 1"));
        return ProfileResponse.builder()
                .username(current.getUsername())
                .nickname(user != null ? user.getNickname() : null)
                .phone(user != null ? user.getPhone() : null)
                .email(user != null ? user.getEmail() : null)
                .gender(user != null ? user.getGender() : null)
                .tenantId(current.getTenantId())
                .tenantName(selectTenantName(current.getTenantId()))
                .roleKeys(current.getRoleKeys())
                .roleNames(roles.stream().map(Role::getRoleName).toList())
                .createTime(user != null ? user.getCreateTime() : null)
                .lastLoginTime(last != null ? last.getLoginTime() : null)
                .lastLoginIp(last != null ? last.getIp() : null)
                .build();
    }

    @Override
    public void updateProfile(LoginUser current, ProfileUpdateRequest request) {
        // 格式校验与落库均在用户服务完成，本处只做委派，保持单一数据入口
        userService.updateMyProfile(current, request.getNickname(),
                request.getPhone(), request.getEmail(), request.getGender());
    }

    @Override
    public List<LoginLog> myLoginLogs(LoginUser current, int limit) {
        // 服务端限幅防拉全表；含失败记录便于用户发现异常登录尝试
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        return loginLogMapper.selectList(new LambdaQueryWrapper<LoginLog>()
                .eq(LoginLog::getUsername, current.getUsername())
                .eq(LoginLog::getEventType, LoginLog.TYPE_LOGIN)
                .orderByDesc(LoginLog::getLoginTime)
                .last("LIMIT " + safeLimit));
    }

    /**
     * 落登录日志（异步）：字段快照在当前线程构建（IP 取自 IpContextHolder），
     * DB 写入移交 boundedElastic 独立线程，不拖慢登录/登出响应；
     * 写入失败只告警，不阻断认证主流程。
     */
    private void recordLoginLog(String username, Long tenantId, String eventType, int status, String msg) {
        LoginLog loginLog = new LoginLog();
        loginLog.setUsername(username);
        loginLog.setTenantId(tenantId);
        loginLog.setEventType(eventType);
        loginLog.setStatus(status);
        loginLog.setMsg(msg);
        loginLog.setIp(IpContextHolder.getIp());
        loginLog.setLoginTime(LocalDateTime.now());
        // 当前线程 MDC 尚存，快照下来供异步线程重新注入，日志写入也带同一 traceId
        String traceId = MDC.get(TraceFilter.MDC_KEY);
        // fire-and-forget：异步落库，失败仅记录告警日志（订阅时才真正执行）
        Mono.fromRunnable(() -> {
                    ReactiveSupport.putTrace(traceId);
                    try {
                        loginLogMapper.insert(loginLog);
                    } finally {
                        MDC.remove(TraceFilter.MDC_KEY);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(unused -> {
                }, e -> log.warn("record login log failed, username={}, eventType={}", username, eventType, e));
    }
}
