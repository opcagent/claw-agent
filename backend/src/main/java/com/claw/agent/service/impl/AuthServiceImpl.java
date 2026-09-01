package com.claw.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.claw.agent.common.BizException;
import com.claw.agent.common.IpContextHolder;
import com.claw.agent.common.ReactiveSupport;
import com.claw.agent.common.ResultCode;
import com.claw.agent.common.RoleConstants;
import com.claw.agent.config.infra.TraceFilter;
import com.claw.agent.mapper.LoginLogMapper;
import com.claw.agent.mapper.MenuMapper;
import com.claw.agent.mapper.RoleMapper;
import com.claw.agent.mapper.TenantMapper;
import com.claw.agent.mapper.UserMapper;
import com.claw.agent.model.LoginLog;
import com.claw.agent.model.Menu;
import com.claw.agent.model.Role;
import com.claw.agent.model.Tenant;
import com.claw.agent.model.User;
import com.claw.agent.model.dto.ChangePasswordRequest;
import com.claw.agent.model.dto.LoginRequest;
import com.claw.agent.model.dto.LoginResponse;
import com.claw.agent.model.dto.ProfileResponse;
import com.claw.agent.model.dto.ProfileUpdateRequest;
import com.claw.agent.security.JwtUtil;
import com.claw.agent.security.LoginRateLimiter;
import com.claw.agent.security.LoginUser;
import com.claw.agent.service.AuthService;
import com.claw.agent.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 认证业务实现：登录 / 修改密码 / 登出 / 当前用户信息。
 * <p>
 * RBAC：登录时经 sys_user_role + sys_role 聚合角色键写入 JWT，
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
    private final LoginLogMapper loginLogMapper;
    private final TenantMapper tenantMapper;
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
            recordLoginLog(user.getUsername(), user.getTenantId(), LoginLog.TYPE_LOGIN, 0, "账号已禁用");
            throw new BizException(ResultCode.USER_DISABLED);
        }
        List<Role> roles = roleMapper.selectRolesByUserId(user.getId());
        List<String> roleKeys = roles.stream().map(Role::getRoleKey).toList();
        List<String> permissions = roleKeys.contains(RoleConstants.ROLE_ADMIN)
                ? List.of(RoleConstants.ALL_PERMISSIONS)
                : menuMapper.selectPermsByUserId(user.getId());
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getTenantId(), roleKeys, permissions);
        // 登录成功清零失败计数（同维度历史失败不再影响后续登录）
        loginRateLimiter.clear(user.getUsername(), clientIp);
        recordLoginLog(user.getUsername(), user.getTenantId(), LoginLog.TYPE_LOGIN, 1, "登录成功");
        log.info("用户登录成功: {}", user.getUsername());
        return LoginResponse.builder()
                .token(token)
                .username(user.getUsername())
                .nickname(user.getNickname())
                .tenantId(user.getTenantId())
                .tenantName(selectTenantName(user.getTenantId()))
                .roles(roleKeys)
                .permissions(permissions)
                .build();
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
        String userId = selectUserId(current.getUsername());
        // 账号被删除后 JWT 未到期仍可调用：空权限处理，避免 null 主键进联表查询
        List<String> permissions = current.isAdmin()
                ? List.of(RoleConstants.ALL_PERMISSIONS)
                : (userId == null ? List.of() : menuMapper.selectPermsByUserId(userId));
        return LoginResponse.builder()
                .username(current.getUsername())
                .tenantId(current.getTenantId())
                .tenantName(selectTenantName(current.getTenantId()))
                .roles(current.getRoleKeys())
                .permissions(permissions)
                .build();
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
        // 平台管理员不受角色菜单授权约束（JWT 权限为 *:*:*），短路返回全部启用目录/菜单，
        // 否则角色授权页取消勾选后管理员自己会丢菜单入口，无法自救
        if (current.isAdmin()) {
            return menuMapper.selectList(new LambdaQueryWrapper<Menu>()
                    .eq(Menu::getStatus, 1)
                    .in(Menu::getMenuType, "M", "C")
                    .orderByAsc(Menu::getParentId, Menu::getOrderNum));
        }
        String userId = current.getUserId() != null ? current.getUserId() : selectUserId(current.getUsername());
        if (userId == null) {
            return List.of();
        }
        return menuMapper.selectMenusByUserId(userId);
    }

    @Override
    public ProfileResponse profile(LoginUser current) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, current.getUsername()).last("LIMIT 1"));
        // 角色名称与键同序下发，供个人中心展示中文角色名
        List<Role> roles = user == null ? List.of() : roleMapper.selectRolesByUserId(user.getId());
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
