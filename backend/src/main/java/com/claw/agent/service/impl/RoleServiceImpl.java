package com.claw.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.claw.agent.common.BizException;
import com.claw.agent.common.ResultCode;
import com.claw.agent.common.RoleConstants;
import com.claw.agent.mapper.MenuMapper;
import com.claw.agent.mapper.RoleMapper;
import com.claw.agent.mapper.RoleMenuMapper;
import com.claw.agent.mapper.UserTenantMapper;
import com.claw.agent.model.Menu;
import com.claw.agent.model.Role;
import com.claw.agent.model.RoleMenu;
import com.claw.agent.model.UserTenant;
import com.claw.agent.security.LoginUser;
import com.claw.agent.service.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色管理服务实现。
 * <p>
 * 业务规则：角色 roleKey 租户内唯一（登录时写入 JWT 驱动鉴权，不可修改）；
 * 已分配给用户的角色禁止删除；
 * 菜单授权（角色拥有哪些菜单/按钮权限）通过 sys_role_menu 全量替换维护。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleServiceImpl extends ServiceImpl<RoleMapper, Role> implements RoleService {

    private final RoleMenuMapper roleMenuMapper;
    private final UserTenantMapper userTenantMapper;
    private final MenuMapper menuMapper;

    @Override
    public List<Role> listRoles(LoginUser current) {
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<Role>()
                .orderByAsc(Role::getRoleSort);
        // 平台管理员跨租户查看全部角色，租户管理员只看本租户
        if (!current.isAdmin()) {
            wrapper.eq(Role::getTenantId, current.getTenantId());
        }
        return baseMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addRole(LoginUser current, Role role) {
        checkReservedRoleKey(role.getRoleKey());
        checkRoleKeyUnique(current, role.getRoleKey(), null);
        role.setTenantId(current.getTenantId());
        role.setDataScope(role.getDataScope() == null ? Role.DATA_SCOPE_SELF : role.getDataScope());
        role.setStatus(role.getStatus() == null ? 1 : role.getStatus());
        baseMapper.insert(role);
        log.info("角色已创建: roleKey={}, name={}, operator={}",
                role.getRoleKey(), role.getRoleName(), current.getUsername());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(LoginUser current, Long id, Role role) {
        Role existed = selectInTenant(current, id);
        // 内置角色禁止禁用：停用 admin/tenant_admin/common 将导致平台或租户鉴权链路断裂
        if (BUILTIN_ROLE_KEYS.contains(existed.getRoleKey())
                && role.getStatus() != null && role.getStatus() == 0) {
            throw new BizException(ResultCode.PARAM_ERROR,
                    "内置角色「" + existed.getRoleName() + "」不可禁用");
        }
        existed.setRoleName(role.getRoleName());
        existed.setRoleSort(role.getRoleSort());
        existed.setDataScope(role.getDataScope());
        existed.setStatus(role.getStatus());
        existed.setRemark(role.getRemark());
        baseMapper.updateById(existed);
    }

    /** 内置角色键集合：admin / tenant_admin / common 为系统内置角色，禁止删除 */
    private static final Set<String> BUILTIN_ROLE_KEYS = Set.of(
            RoleConstants.ROLE_ADMIN,
            RoleConstants.ROLE_TENANT_ADMIN,
            RoleConstants.ROLE_COMMON
    );

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(LoginUser current, Long id) {
        Role existed = selectInTenant(current, id);
        // 三个内置角色禁删：删除后系统鉴权链路断裂（admin 失平台入口、tenant_admin 失租户管理、common 失普通用户基线）
        if (BUILTIN_ROLE_KEYS.contains(existed.getRoleKey())) {
            throw new BizException(ResultCode.PARAM_ERROR,
                    "内置角色「" + existed.getRoleName() + "」不可删除");
        }
        Long assigned = userTenantMapper.selectCount(new LambdaQueryWrapper<UserTenant>()
                .eq(UserTenant::getRoleId, id));
        if (assigned != null && assigned > 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "角色已分配给用户，禁止删除");
        }
        // 同步清理角色-菜单授权关系，避免残留脏数据
        roleMenuMapper.delete(new LambdaQueryWrapper<RoleMenu>().eq(RoleMenu::getRoleId, id));
        baseMapper.deleteById(id);
    }

    @Override
    public List<Long> listRoleMenus(LoginUser current, Long id) {
        selectInTenant(current, id);
        return roleMenuMapper.selectList(new LambdaQueryWrapper<RoleMenu>()
                        .eq(RoleMenu::getRoleId, id))
                .stream().map(RoleMenu::getMenuId).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveRoleMenus(LoginUser current, Long id, List<Long> menuIds) {
        selectInTenant(current, id);
        roleMenuMapper.delete(new LambdaQueryWrapper<RoleMenu>().eq(RoleMenu::getRoleId, id));
        List<Long> finalMenuIds = menuIds;
        if (menuIds != null && !menuIds.isEmpty() && !current.isAdmin()) {
            // 纵深防御：租户专属菜单（perms 前缀 system:tenant）不对非平台管理员开放，
            // 授权时静默过滤，防止租户管理员给自己角色勾出租户管理权限点；
            // 历史脏数据重保存时同样被清除（全量替换语义）
            Set<Long> tenantOnlyIds = menuMapper.selectList(new LambdaQueryWrapper<Menu>()
                            .in(Menu::getId, menuIds)
                            .likeRight(Menu::getPerms, "system:tenant"))
                    .stream().map(Menu::getId).collect(Collectors.toSet());
            finalMenuIds = menuIds.stream()
                    .filter(menuId -> !tenantOnlyIds.contains(menuId))
                    .toList();
        }
        if (finalMenuIds != null) {
            for (Long menuId : finalMenuIds) {
                RoleMenu rm = new RoleMenu();
                rm.setRoleId(id);
                rm.setMenuId(menuId);
                roleMenuMapper.insert(rm);
            }
        }
        log.info("角色菜单授权已保存: roleId={}, count={}, operator={}",
                id, finalMenuIds == null ? 0 : finalMenuIds.size(), current.getUsername());
    }

    // ------------------------------------------------------------
    // 内部辅助
    // ------------------------------------------------------------

    /** 平台超管角色键禁止在租户级创建：角色键直接写入 JWT 驱动鉴权，伪造 admin 键即可提权为平台管理员 */
    private void checkReservedRoleKey(String roleKey) {
        if (RoleConstants.ROLE_ADMIN.equals(roleKey)) {
            throw new BizException(ResultCode.FORBIDDEN, "平台超管角色键 admin 不可创建");
        }
    }

    /** roleKey 租户内唯一校验（更新时排除自身） */
    private void checkRoleKeyUnique(LoginUser current, String roleKey, Long excludeId) {
        LambdaQueryWrapper<Role> w = new LambdaQueryWrapper<Role>()
                .eq(Role::getTenantId, current.getTenantId())
                .eq(Role::getRoleKey, roleKey);
        if (excludeId != null) {
            w.ne(Role::getId, excludeId);
        }
        Long count = baseMapper.selectCount(w);
        if (count != null && count > 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "角色权限字符已存在");
        }
    }

    /** 租户内角色查询（越租户访问返回 404，防信息泄漏）；平台管理员可操作任意租户角色 */
    private Role selectInTenant(LoginUser current, Long id) {
        Role existed = baseMapper.selectById(id);
        if (existed == null) {
            throw new BizException(ResultCode.NOT_FOUND, "角色不存在");
        }
        if (current.isAdmin()) {
            return existed;
        }
        if (!current.getTenantId().equals(existed.getTenantId())) {
            throw new BizException(ResultCode.NOT_FOUND, "角色不存在");
        }
        return existed;
    }
}
