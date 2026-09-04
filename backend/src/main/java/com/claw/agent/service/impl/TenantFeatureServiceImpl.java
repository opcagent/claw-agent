package com.claw.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.claw.agent.common.BizException;
import com.claw.agent.common.ResultCode;
import com.claw.agent.common.RoleConstants;
import com.claw.agent.mapper.MenuMapper;
import com.claw.agent.mapper.RoleMapper;
import com.claw.agent.mapper.RoleMenuMapper;
import com.claw.agent.mapper.TenantFeatureMapper;
import com.claw.agent.model.Menu;
import com.claw.agent.model.Role;
import com.claw.agent.model.RoleMenu;
import com.claw.agent.model.TenantFeature;
import com.claw.agent.security.LoginUser;
import com.claw.agent.service.TenantFeatureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 租户功能模块配置服务实现。
 * <p>
 * 平台管理员可为每个租户配置可用的功能模块（菜单），
 * 租户管理员只能使用平台管理员配置的功能模块。
 * 未配置的租户默认拥有全部功能（向后兼容）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantFeatureServiceImpl extends ServiceImpl<TenantFeatureMapper, TenantFeature> implements TenantFeatureService {

    private final TenantFeatureMapper tenantFeatureMapper;
    private final RoleMapper roleMapper;
    private final MenuMapper menuMapper;
    private final RoleMenuMapper roleMenuMapper;

    @Override
    public List<Long> getTenantFeatureMenuIds(Long tenantId) {
        // 使用 BaseMapper 内置方法，替代原 XML 的 selectEnabledMenuIds
        List<TenantFeature> features = tenantFeatureMapper.selectList(
                new LambdaQueryWrapper<TenantFeature>()
                        .eq(TenantFeature::getTenantId, tenantId)
                        .eq(TenantFeature::getEnabled, 1)
                        .select(TenantFeature::getMenuId)
                        .orderByAsc(TenantFeature::getMenuId));
        List<Long> menuIds = features.stream().map(TenantFeature::getMenuId).collect(Collectors.toList());
        // 返回 null 表示没有配置，拥有全部功能（向后兼容）
        return menuIds.isEmpty() ? null : menuIds;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveTenantFeatures(LoginUser current, Long tenantId, List<Long> menuIds) {
        // 仅平台管理员可操作
        if (!current.isAdmin()) {
            throw new BizException(ResultCode.FORBIDDEN, "仅平台管理员可配置租户功能");
        }

        if (tenantId == null || tenantId <= 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "租户ID无效");
        }

        // 批量保存（先删后插，拆分为两条 SQL 避免 MySQL allowMultiQueries 问题）
        tenantFeatureMapper.delete(
                new LambdaQueryWrapper<TenantFeature>()
                        .eq(TenantFeature::getTenantId, tenantId));

        if (menuIds == null || menuIds.isEmpty()) {
            log.info("已清空租户功能配置: tenantId={}, 恢复全部功能", tenantId);
        } else {
            // 使用 saveBatch 触发 AuditMetaObjectHandler 自动填充审计字段
            List<TenantFeature> features = menuIds.stream().map(menuId -> {
                TenantFeature f = new TenantFeature();
                f.setTenantId(tenantId);
                f.setMenuId(menuId);
                f.setEnabled(1);
                return f;
            }).collect(Collectors.toList());
            this.saveBatch(features);
            log.info("已保存租户功能配置: tenantId={}, menuCount={}", tenantId, menuIds.size());
        }

        // 增量同步租户内所有角色的 role_menu，确保不超出 tenant_feature 配置范围
        syncAllRolesMenu(tenantId);
    }

    @Override
    public List<Menu> filterMenusByTenant(Long tenantId, List<Menu> menus) {
        // 平台管理员（tenantId=0）不过滤
        if (tenantId == null || tenantId == 0) {
            return menus;
        }

        // 获取租户功能配置
        List<Long> enabledMenuIds = getTenantFeatureMenuIds(tenantId);

        // 没有配置记录，返回全部菜单（向后兼容）
        if (enabledMenuIds == null) {
            return menus;
        }

        // 过滤菜单
        Set<Long> enabledSet = enabledMenuIds.stream().collect(Collectors.toSet());
        return menus.stream()
                .filter(menu -> enabledSet.contains(menu.getId()))
                .collect(Collectors.toList());
    }

    /**
     * 增量同步租户内所有角色的 role_menu 关联。
     * <p>
     * 平台管理员配置租户功能后，该租户下所有角色的菜单权限都必须在配置范围内。
     * 采用增量更新（仅添加/删除差异项），保留租户管理员对角色的自定义授权。
     * <ul>
     *   <li>新增：配置中包含但角色未授权的菜单 → 插入</li>
     *   <li>移除：角色已授权但不在配置中的菜单 → 删除</li>
     *   <li>保留：配置中包含且角色已授权的菜单 → 不动</li>
     * </ul>
     *
     * @param tenantId 租户ID
     */
    private void syncAllRolesMenu(Long tenantId) {
        // 查询该租户的全部角色
        List<Role> roles = roleMapper.selectList(new LambdaQueryWrapper<Role>()
                .eq(Role::getTenantId, tenantId));
        if (roles.isEmpty()) {
            return;
        }

        // 查询全部启用菜单
        List<Menu> allMenus = menuMapper.selectList(new LambdaQueryWrapper<Menu>()
                .eq(Menu::getStatus, 1));

        // 构建允许的菜单ID集合：有配置按配置，无配置则全部可用（排除租户管理模块）
        List<Long> configuredMenuIds = getTenantFeatureMenuIds(tenantId);
        Set<Long> allowedMenuIds = allMenus.stream()
                .filter(m -> configuredMenuIds == null || configuredMenuIds.contains(m.getId()))
                .filter(m -> m.getPerms() == null || !m.getPerms().startsWith(RoleConstants.PERMS_TENANT_PREFIX))
                .map(Menu::getId)
                .collect(Collectors.toSet());

        for (Role role : roles) {
            // 当前角色已有的菜单ID
            List<RoleMenu> existingRms = roleMenuMapper.selectList(new LambdaQueryWrapper<RoleMenu>()
                    .eq(RoleMenu::getRoleId, role.getId()));
            Set<Long> existingMenuIds = existingRms.stream()
                    .map(RoleMenu::getMenuId)
                    .collect(Collectors.toSet());

            // 新增：allowed 中有但角色没有 → 插入
            for (Long menuId : allowedMenuIds) {
                if (!existingMenuIds.contains(menuId)) {
                    RoleMenu rm = new RoleMenu();
                    rm.setRoleId(role.getId());
                    rm.setMenuId(menuId);
                    roleMenuMapper.insert(rm);
                }
            }

            // 移除：角色有但 allowed 中没有 → 删除
            for (Long menuId : existingMenuIds) {
                if (!allowedMenuIds.contains(menuId)) {
                    roleMenuMapper.delete(new LambdaQueryWrapper<RoleMenu>()
                            .eq(RoleMenu::getRoleId, role.getId())
                            .eq(RoleMenu::getMenuId, menuId));
                }
            }
        }

        log.info("已增量同步租户全部角色菜单: tenantId={}, roleCount={}, allowedMenuCount={}",
                tenantId, roles.size(), allowedMenuIds.size());
    }
}
