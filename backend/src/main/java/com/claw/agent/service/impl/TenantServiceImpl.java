package com.claw.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.claw.agent.common.BizException;
import com.claw.agent.common.ResultCode;
import com.claw.agent.common.RoleConstants;
import com.claw.agent.mapper.*;
import com.claw.agent.model.*;
import com.claw.agent.model.dto.SetAdminRequest;
import com.claw.agent.model.dto.TenantCreateWithAdminRequest;
import com.claw.agent.security.LoginUser;
import com.claw.agent.service.TenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 租户管理服务实现。
 * <p>
 * 业务规则：租户编码全局唯一；已挂用户的租户禁止删除（防孤儿数据）；
 * 编码不可修改（历史数据按编码关联，改了会失联）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantServiceImpl extends ServiceImpl<TenantMapper, Tenant> implements TenantService {

    /** 租户模块权限标识前缀（新租户管理员授权时整体排除，覆盖租户菜单与其增删改按钮） */
    private static final String PERMS_TENANT_PREFIX = "system:tenant:";

    /** 智能对话菜单权限标识（普通用户唯一可用菜单） */
    private static final String PERMS_CHAT_USE = "chat:use";

    private final UserMapper userMapper;
    private final DeptMapper deptMapper;
    private final RoleMapper roleMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final MenuMapper menuMapper;
    private final UserTenantMapper userTenantMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public List<Tenant> listTenants() {
        return baseMapper.selectList(new LambdaQueryWrapper<Tenant>().orderByAsc(Tenant::getId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addTenant(LoginUser current, Tenant tenant) {
        Long existed = baseMapper.selectCount(new LambdaQueryWrapper<Tenant>()
                .eq(Tenant::getTenantCode, tenant.getTenantCode()));
        if (existed != null && existed > 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "租户编码已存在");
        }
        tenant.setStatus(tenant.getStatus() == null ? 1 : tenant.getStatus());
        baseMapper.insert(tenant);
        // 新租户必须同步初始化组织骨架，否则角色/部门为空，管理员无法管理也无法分配角色
        initTenantSkeleton(tenant);
        log.info("租户已创建: code={}, name={}, operator={}",
                tenant.getTenantCode(), tenant.getTenantName(), current.getUsername());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTenant(Long id, Tenant tenant) {
        Tenant existed = baseMapper.selectById(id);
        if (existed == null) {
            throw new BizException(ResultCode.NOT_FOUND, "租户不存在");
        }
        existed.setTenantName(tenant.getTenantName());
        // status 为 NOT NULL 列：请求未带时保留原值，避免空值穿透报 SQL 约束异常（500）
        if (tenant.getStatus() != null) {
            existed.setStatus(tenant.getStatus());
        }
        existed.setRemark(tenant.getRemark());
        baseMapper.updateById(existed);
    }

    @Override
    public void deleteTenant(Long id) {
        // 租户禁止物理删除：删除后该组织下所有用户/角色/部门/日志等关联数据全部丢失，不可恢复
        // 如需停用租户，请通过修改接口将 status 置为 0（禁用）
        throw new BizException(ResultCode.PARAM_ERROR, "租户不支持删除，可通过修改状态为禁用来停用");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setTenantAdmin(LoginUser current, Long tenantId, SetAdminRequest request) {
        // 1. 检查租户是否存在
        Tenant tenant = baseMapper.selectById(tenantId);
        if (tenant == null) {
            throw new BizException(ResultCode.NOT_FOUND, "租户不存在");
        }

        // 2. 检查用户是否存在且属于该租户（通过 sys_user_tenant 关联校验）
        User user = userMapper.selectById(request.getUserId());
        if (user == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "用户不存在");
        }
        Long utCount = userTenantMapper.selectCount(new LambdaQueryWrapper<UserTenant>()
                .eq(UserTenant::getUserId, user.getId())
                .eq(UserTenant::getTenantId, tenantId)
                .eq(UserTenant::getStatus, 1));
        if (utCount == null || utCount == 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "用户不属于该租户");
        }

        // 3. 查找该租户的 tenant_admin 角色
        Role adminRole = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getTenantId, tenantId)
                .eq(Role::getRoleKey, RoleConstants.ROLE_TENANT_ADMIN)
                .last("LIMIT 1"));
        if (adminRole == null) {
            throw new BizException(ResultCode.NOT_FOUND, "租户管理员角色不存在，请先在角色管理中创建 tenant_admin 角色");
        }

        // 4. 全量替换用户在该组织内的角色（仅保留 tenant_admin 角色），保留组织属性
        // 先读取现有记录的组织属性（dept_id/position），避免角色变更时丢失
        UserTenant existingUt = userTenantMapper.selectOne(new LambdaQueryWrapper<UserTenant>()
                .eq(UserTenant::getUserId, user.getId())
                .eq(UserTenant::getTenantId, tenantId)
                .last("LIMIT 1"));
        Long preservedDeptId = existingUt != null ? existingUt.getDeptId() : null;
        String preservedPosition = existingUt != null ? existingUt.getPosition() : null;

        userTenantMapper.delete(new LambdaQueryWrapper<UserTenant>()
                .eq(UserTenant::getUserId, user.getId())
                .eq(UserTenant::getTenantId, tenantId));
        UserTenant ut = new UserTenant();
        ut.setUserId(user.getId());
        ut.setTenantId(tenantId);
        ut.setRoleId(adminRole.getId());
        ut.setDeptId(preservedDeptId);
        ut.setPosition(preservedPosition);
        ut.setStatus(1);
        ut.setIsDefault(1);
        userTenantMapper.insert(ut);

        log.info("租户管理员已设置: tenant={}, user={}, operator={}",
                tenant.getTenantName(), user.getUsername(), current.getUsername());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addTenantWithAdmin(LoginUser current, TenantCreateWithAdminRequest request) {
        // 1. 校验租户编码唯一性
        Long existed = baseMapper.selectCount(new LambdaQueryWrapper<Tenant>()
                .eq(Tenant::getTenantCode, request.getTenantCode()));
        if (existed != null && existed > 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "租户编码已存在");
        }

        // 2. 创建租户
        Tenant tenant = new Tenant();
        tenant.setTenantCode(request.getTenantCode());
        tenant.setTenantName(request.getTenantName());
        tenant.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        tenant.setRemark(request.getRemark());
        baseMapper.insert(tenant);

        // 3. 初始化组织骨架（根部门 + 内置角色 + 菜单授权）
        initTenantSkeleton(tenant);

        // 4. 如果提供了管理员信息，创建管理员用户
        if (request.getAdminUsername() != null && !request.getAdminUsername().trim().isEmpty()) {
            // 校验用户名全局唯一
            Long userExisted = userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getUsername, request.getAdminUsername()));
            if (userExisted != null && userExisted > 0) {
                throw new BizException(ResultCode.USER_EXISTS, "用户名已存在");
            }

            // 校验密码必填
            if (request.getAdminPassword() == null || request.getAdminPassword().trim().isEmpty()) {
                throw new BizException(ResultCode.PARAM_ERROR, "管理员密码不能为空");
            }

            // 查找该租户的 tenant_admin 角色
            Role adminRole = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                    .eq(Role::getTenantId, tenant.getId())
                    .eq(Role::getRoleKey, RoleConstants.ROLE_TENANT_ADMIN)
                    .last("LIMIT 1"));
            if (adminRole == null) {
                throw new BizException(ResultCode.NOT_FOUND, "租户管理员角色不存在");
            }

            // 创建管理员用户并建立组织关联
            User adminUser = new User();
            adminUser.setUsername(request.getAdminUsername().trim());
            adminUser.setPassword(passwordEncoder.encode(request.getAdminPassword()));
            adminUser.setNickname(request.getAdminNickname());
            adminUser.setPhone(request.getAdminPhone());
            adminUser.setEmail(request.getAdminEmail());
            adminUser.setGender(request.getAdminGender() == null ? 0 : request.getAdminGender());
            adminUser.setStatus(1);
            // 生成规则ID：租户编码_自增序号（与 UserServiceImpl 保持一致）
            String tenantCode = request.getTenantCode();
            adminUser.setId(tenantCode + "_1");
            userMapper.insert(adminUser);

            // 创建 sys_user_tenant 关联（分配 tenant_admin 角色）
            UserTenant ut = new UserTenant();
            ut.setUserId(adminUser.getId());
            ut.setTenantId(tenant.getId());
            ut.setRoleId(adminRole.getId());
            ut.setStatus(1);
            ut.setIsDefault(1);
            userTenantMapper.insert(ut);

            log.info("租户及初始管理员已创建: tenant={}, adminUser={}, operator={}",
                    tenant.getTenantName(), adminUser.getUsername(), current.getUsername());
        } else {
            log.info("租户已创建（无初始管理员）: tenant={}, operator={}",
                    tenant.getTenantName(), current.getUsername());
        }
    }

    // ------------------------------------------------------------
    // 内部辅助
    // ------------------------------------------------------------

    /**
     * 初始化新租户组织骨架：根部门 + 内置租户管理员/普通用户角色 + 菜单授权，
     * 与 V4 默认租户种子数据的形态保持一致。
     *
     * @param tenant 已落库的租户（依赖回填的主键）
     */
    private void initTenantSkeleton(Tenant tenant) {
        Long tenantId = tenant.getId();
        // 根部门
        Dept root = new Dept();
        root.setTenantId(tenantId);
        root.setParentId(0L);
        root.setAncestors("0");
        root.setDeptName(tenant.getTenantName() + "总部");
        root.setOrderNum(0);
        root.setStatus(1);
        deptMapper.insert(root);
        // 内置角色（角色键写入 JWT 驱动鉴权，与默认租户保持一致）
        Role tenantAdmin = buildRole(tenantId, RoleConstants.ROLE_TENANT_ADMIN, "租户管理员",
                1, Role.DATA_SCOPE_DEPT_AND_CHILD);
        roleMapper.insert(tenantAdmin);
        Role common = buildRole(tenantId, RoleConstants.ROLE_COMMON, "普通用户",
                2, Role.DATA_SCOPE_SELF);
        roleMapper.insert(common);
        // 菜单授权：租户管理员除租户管理外全部；普通用户仅智能对话（含其父目录）
        List<Menu> menus = menuMapper.selectList(new LambdaQueryWrapper<Menu>()
                .eq(Menu::getStatus, 1));
        for (Menu menu : menus) {
            // 租户模块为平台管理员专属：菜单与按钮（system:tenant:*）整体排除
            if (menu.getPerms() != null && menu.getPerms().startsWith(PERMS_TENANT_PREFIX)) {
                continue;
            }
            insertRoleMenu(tenantAdmin.getId(), menu.getId());
        }
        menus.stream()
                .filter(m -> PERMS_CHAT_USE.equals(m.getPerms()))
                .findFirst()
                .ifPresent(chatMenu -> {
                    insertRoleMenu(common.getId(), chatMenu.getId());
                    if (chatMenu.getParentId() != null && chatMenu.getParentId() != 0L) {
                        insertRoleMenu(common.getId(), chatMenu.getParentId());
                    }
                });
    }

    /** 构建内置角色（状态默认启用） */
    private Role buildRole(Long tenantId, String roleKey, String roleName, int sort, int dataScope) {
        Role role = new Role();
        role.setTenantId(tenantId);
        role.setRoleKey(roleKey);
        role.setRoleName(roleName);
        role.setRoleSort(sort);
        role.setDataScope(dataScope);
        role.setStatus(1);
        return role;
    }

    /** 写入角色-菜单关联 */
    private void insertRoleMenu(Long roleId, Long menuId) {
        RoleMenu rm = new RoleMenu();
        rm.setRoleId(roleId);
        rm.setMenuId(menuId);
        roleMenuMapper.insert(rm);
    }
}
