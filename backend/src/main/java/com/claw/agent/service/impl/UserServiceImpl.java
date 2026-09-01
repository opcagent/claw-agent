package com.claw.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.claw.agent.common.BizException;
import com.claw.agent.common.ResultCode;
import com.claw.agent.common.RoleConstants;
import com.claw.agent.mapper.ChatSessionMapper;
import com.claw.agent.mapper.DeptMapper;
import com.claw.agent.mapper.RoleMapper;
import com.claw.agent.mapper.UserMapper;
import com.claw.agent.mapper.UserRoleMapper;
import com.claw.agent.model.ChatSession;
import com.claw.agent.model.Dept;
import com.claw.agent.model.Role;
import com.claw.agent.mapper.TenantMapper;
import com.claw.agent.model.Tenant;
import com.claw.agent.model.User;
import com.claw.agent.model.UserRole;
import com.claw.agent.model.dto.UserCreateRequest;
import com.claw.agent.security.LoginUser;
import com.claw.agent.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 用户管理服务实现。
 * <p>
 * 业务规则：用户名全局唯一、密码 BCrypt 加密、跨租户访问一律 404；
 * 角色分配全量替换且仅允许本租户角色（防跨租户提权）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    /** 手机号格式：1 开头的 11 位数字 */
    public static final Pattern PHONE_PATTERN = Pattern.compile("^1\\d{10}$");
    /** 邮箱格式（宽松）：常规邮箱正则 */
    public static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final DeptMapper deptMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final TenantMapper tenantMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public List<User> listUsers(LoginUser current) {
        List<User> users = baseMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getTenantId, current.getTenantId())
                .orderByAsc(User::getId));
        users.forEach(u -> u.setPassword(null));
        return users;
    }

    @Override
    public List<User> listUsersByTenant(LoginUser current, Long tenantId) {
        // 仅平台管理员可跨租户查询，租户管理员只能查本租户
        Long targetTenant = current.isAdmin() ? tenantId : current.getTenantId();
        List<User> users = baseMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getTenantId, targetTenant)
                .orderByAsc(User::getId));
        users.forEach(u -> u.setPassword(null));
        return users;
    }

    /** 分页单页条数上限 */
    private static final long MAX_PAGE_SIZE = 100;

    @Override
    public IPage<User> pageUsers(LoginUser current, long pageNum, long pageSize,
                                  String keyword, Integer status, Long deptId) {
        // 入参收敛：防负数/超大分页拖垮数据库（分页插件 maxLimit 仅兜底）
        long safePage = Math.max(1, pageNum);
        long safeSize = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(User::getTenantId, current.getTenantId());
        // 关键词模糊搜索：匹配用户名/昵称/手机/邮箱任一即命中
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(User::getUsername, kw)
                    .or().like(User::getNickname, kw)
                    .or().like(User::getPhone, kw)
                    .or().like(User::getEmail, kw));
        }
        // 状态筛选
        if (status != null) {
            wrapper.eq(User::getStatus, status);
        }
        // 部门筛选
        if (deptId != null) {
            wrapper.eq(User::getDeptId, deptId);
        }
        wrapper.orderByAsc(User::getId);
        IPage<User> page = baseMapper.selectPage(new Page<>(safePage, safeSize), wrapper);
        // 密码不下发（与 listUsers 同源的脱敏规则）
        page.getRecords().forEach(u -> u.setPassword(null));
        return page;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addUser(LoginUser current, UserCreateRequest request) {
        Long existed = baseMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));
        if (existed != null && existed > 0) {
            throw new BizException(ResultCode.USER_EXISTS);
        }
        User user = new User();
        user.setTenantId(current.getTenantId());
        checkDeptInTenant(current, request.getDeptId());
        user.setDeptId(request.getDeptId());
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        fillContact(user, request.getPhone(), request.getEmail(), request.getGender());
        user.setStatus(1);
        user.setRemark(request.getRemark());
        // 生成规则ID：租户编码_自增序号
        user.setId(generateUserId(current.getTenantId()));
        baseMapper.insert(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(LoginUser current, String id, User user) {
        User existed = selectInTenant(current, id);
        checkPlatformAdminProtected(current, existed);
        checkDeptInTenant(current, user.getDeptId());
        existed.setNickname(user.getNickname());
        fillContact(existed, user.getPhone(), user.getEmail(), user.getGender());
        existed.setDeptId(user.getDeptId());
        // status 为 NOT NULL 列：请求未带时保留原值，避免空值穿透报 SQL 约束异常（500）
        if (user.getStatus() != null) {
            existed.setStatus(user.getStatus());
        }
        existed.setRemark(user.getRemark());
        baseMapper.updateById(existed);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(LoginUser current, String id, String newPassword) {
        User existed = selectInTenant(current, id);
        checkPlatformAdminProtected(current, existed);
        existed.setPassword(passwordEncoder.encode(newPassword));
        baseMapper.updateById(existed);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(LoginUser current, String id) {
        User existed = selectInTenant(current, id);
        checkPlatformAdminProtected(current, existed);
        if (current.getUsername().equals(existed.getUsername())) {
            throw new BizException(ResultCode.PARAM_ERROR, "不能删除当前登录账号");
        }
        baseMapper.deleteById(id);
        // 同步清理用户-角色关联，避免残留脏数据
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, id));
        // 同步清理会话元数据（按用户名隔离），避免同名再注册时看到前任会话；
        // 登录日志属审计痕迹，刻意保留不删
        chatSessionMapper.delete(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUsername, existed.getUsername()));
    }

    @Override
    public List<Long> listUserRoles(LoginUser current, String id) {
        User existed = selectInTenant(current, id);
        return userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, existed.getId()))
                .stream().map(UserRole::getRoleId).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveUserRoles(LoginUser current, String id, List<Long> roleIds) {
        User existed = selectInTenant(current, id);
        if (roleIds != null) {
            for (Long roleId : roleIds) {
                Role role = roleMapper.selectById(roleId);
                if (role == null || !current.getTenantId().equals(role.getTenantId())) {
                    throw new BizException(ResultCode.PARAM_ERROR, "角色不存在或不属于当前租户");
                }
                // 纵深防御：历史/手工数据若混入 admin 键角色，非平台管理员不得分配（否则直接提权）
                if (RoleConstants.ROLE_ADMIN.equals(role.getRoleKey()) && !current.isAdmin()) {
                    throw new BizException(ResultCode.FORBIDDEN, "平台超管角色仅平台管理员可分配");
                }
            }
        }
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, existed.getId()));
        if (roleIds != null) {
            for (Long roleId : roleIds) {
                UserRole ur = new UserRole();
                ur.setUserId(existed.getId());
                ur.setRoleId(roleId);
                userRoleMapper.insert(ur);
            }
        }
        log.info("用户角色已分配: user={}, count={}, operator={}",
                existed.getUsername(), roleIds == null ? 0 : roleIds.size(), current.getUsername());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMyProfile(LoginUser current, String nickname, String phone,
                                String email, Integer gender) {
        // 本人记录由 JWT 用户名定位，不接受外部传入的用户标识（防越权改他人资料）
        User existed = baseMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, current.getUsername()).last("LIMIT 1"));
        if (existed == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        existed.setNickname(nickname);
        fillContact(existed, phone, email, gender);
        baseMapper.updateById(existed);
    }

    /** 部门归属校验：部门必须存在且属于当前租户（防跨租户引用脏化组织树） */
    private void checkDeptInTenant(LoginUser current, Long deptId) {
        if (deptId == null) {
            return;
        }
        Dept dept = deptMapper.selectById(deptId);
        if (dept == null || !current.getTenantId().equals(dept.getTenantId())) {
            throw new BizException(ResultCode.PARAM_ERROR, "部门不存在或不属于当前租户");
        }
    }

    /**
     * 联系方式字段填充（新增/修改与本人自助更新共用）：
     * 手机/邮箱空白则置空，非法格式直接拒绝；性别只允许 0/1/2，缺省 0。
     */
    public void fillContact(User user, String phone, String email, Integer gender) {
        if (StringUtils.hasText(phone)) {
            String trimmed = phone.trim();
            if (!PHONE_PATTERN.matcher(trimmed).matches()) {
                throw new BizException(ResultCode.PARAM_ERROR, "手机号格式不正确");
            }
            user.setPhone(trimmed);
        } else {
            user.setPhone(null);
        }
        if (StringUtils.hasText(email)) {
            String trimmed = email.trim();
            if (!EMAIL_PATTERN.matcher(trimmed).matches()) {
                throw new BizException(ResultCode.PARAM_ERROR, "邮箱格式不正确");
            }
            user.setEmail(trimmed);
        } else {
            user.setEmail(null);
        }
        user.setGender(gender == null ? 0 : gender);
        if (user.getGender() < 0 || user.getGender() > 2) {
            throw new BizException(ResultCode.PARAM_ERROR, "性别取值非法");
        }
    }

    /** 租户内用户查询（越租户访问返回 404，防止信息泄漏） */
    private User selectInTenant(LoginUser current, String id) {
        User existed = baseMapper.selectById(id);
        if (existed == null || !current.getTenantId().equals(existed.getTenantId())) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return existed;
    }

    /**
     * 平台超管账号保护：目标用户持有 admin 角色键时，非平台管理员一律禁止修改/重置密码/删除。
     * <p>
     * 背景：平台管理员与租户管理员可能同属一个租户，若不加防护，租户管理员可重置同租户内
     * 平台管理员的密码后接管其账号，实现跨租户提权（与 saveUserRoles 的分配防护对齐）。
     *
     * @param current 操作者
     * @param target  目标用户
     */
    private void checkPlatformAdminProtected(LoginUser current, User target) {
        if (current.isAdmin()) {
            return;
        }
        boolean targetIsAdmin = roleMapper.selectRolesByUserId(target.getId()).stream()
                .anyMatch(role -> RoleConstants.ROLE_ADMIN.equals(role.getRoleKey()));
        if (targetIsAdmin) {
            throw new BizException(ResultCode.FORBIDDEN, "平台管理员账号仅平台管理员可操作");
        }
    }

    /**
     * 生成规则用户ID：{租户编码}_{自增序号}。
     * <p>
     * 自增序号按租户维度递增，从 1 开始；查库取当前租户最大序号 +1。
     *
     * @param tenantId 租户ID
     * @return 格式化的用户ID
     */
    private String generateUserId(Long tenantId) {
        String tenantCode;
        if (tenantId == null) {
            tenantCode = "0";
        } else {
            Tenant tenant = tenantMapper.selectById(tenantId);
            tenantCode = (tenant != null && org.springframework.util.StringUtils.hasText(tenant.getTenantCode()))
                    ? tenant.getTenantCode() : String.valueOf(tenantId);
        }
        // 查当前租户下最大序号（按ID倒序取第一条，提取末尾数字部分）
        User lastUser = baseMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getTenantId, tenantId)
                .orderByDesc(User::getId)
                .last("LIMIT 1"));
        int nextSeq = 1;
        if (lastUser != null && lastUser.getId() != null) {
            String lastId = lastUser.getId();
            int underscoreIdx = lastId.lastIndexOf('_');
            if (underscoreIdx > 0 && underscoreIdx < lastId.length() - 1) {
                try {
                    nextSeq = Integer.parseInt(lastId.substring(underscoreIdx + 1)) + 1;
                } catch (NumberFormatException ignored) {
                    // 解析失败从 1 开始
                }
            }
        }
        return tenantCode + "_" + nextSeq;
    }
}
