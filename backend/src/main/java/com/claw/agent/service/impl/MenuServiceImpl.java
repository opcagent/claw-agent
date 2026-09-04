package com.claw.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.claw.agent.common.BizException;
import com.claw.agent.common.ResultCode;
import com.claw.agent.mapper.MenuMapper;
import com.claw.agent.mapper.RoleMapper;
import com.claw.agent.mapper.RoleMenuMapper;
import com.claw.agent.model.Menu;
import com.claw.agent.model.Role;
import com.claw.agent.model.RoleMenu;
import com.claw.agent.security.LoginUser;
import com.claw.agent.service.MenuService;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 菜单/权限点管理服务实现。
 * <p>
 * 菜单为平台级数据（无租户字段），增删改仅平台管理员可操作；
 * 菜单与角色的关联（某菜单挂给了哪些角色）按当前用户租户收窄，
 * 租户管理员可维护本租户角色的关联，关联关系落 sys_role_menu。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MenuServiceImpl extends ServiceImpl<MenuMapper, Menu> implements MenuService {

    private final RoleMapper roleMapper;
    private final RoleMenuMapper roleMenuMapper;
    /** 启用菜单缓存（所有用户共享同一份快照） */
    private final Cache<String, List<Menu>> menuCache;

    /** 菜单缓存键（全局唯一，因为菜单是平台级数据） */
    private static final String MENU_CACHE_KEY = "enabled_menus";

    @Override
    public List<Menu> listEnabledMenus() {
        return menuCache.get(MENU_CACHE_KEY, k ->
                baseMapper.selectList(new LambdaQueryWrapper<Menu>()
                        .eq(Menu::getStatus, 1)
                        .orderByAsc(Menu::getOrderNum)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addMenu(Menu menu) {
        validateMenu(menu);
        if (menu.getParentId() != null && menu.getParentId() != 0L) {
            Menu parent = baseMapper.selectById(menu.getParentId());
            if (parent == null) {
                throw new BizException(ResultCode.PARAM_ERROR, "父菜单不存在");
            }
        }
        menu.setStatus(menu.getStatus() == null ? 1 : menu.getStatus());
        menu.setVisible(menu.getVisible() == null ? 1 : menu.getVisible());
        baseMapper.insert(menu);
        // 菜单变更清空缓存
        menuCache.invalidateAll();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMenu(Long id, Menu menu) {
        Menu existed = baseMapper.selectById(id);
        if (existed == null) {
            throw new BizException(ResultCode.NOT_FOUND, "菜单不存在");
        }
        validateMenu(menu);
        if (menu.getParentId() != null) {
            if (menu.getParentId().equals(id)) {
                throw new BizException(ResultCode.PARAM_ERROR, "父菜单不能是自身");
            }
            // 沿父链上溯，防止把子孙设为父级形成环
            Long cursor = menu.getParentId();
            while (cursor != null && cursor != 0L) {
                Menu parent = baseMapper.selectById(cursor);
                if (parent == null) {
                    throw new BizException(ResultCode.PARAM_ERROR, "父菜单不存在");
                }
                if (parent.getId().equals(id)) {
                    throw new BizException(ResultCode.PARAM_ERROR, "父菜单不能是自身的子孙菜单");
                }
                cursor = parent.getParentId();
            }
        }
        existed.setParentId(menu.getParentId());
        existed.setMenuName(menu.getMenuName());
        existed.setMenuType(menu.getMenuType());
        existed.setOrderNum(menu.getOrderNum());
        existed.setPath(menu.getPath());
        existed.setIcon(menu.getIcon());
        existed.setPerms(menu.getPerms());
        existed.setVisible(menu.getVisible());
        existed.setStatus(menu.getStatus());
        baseMapper.updateById(existed);
        // 菜单变更清空缓存
        menuCache.invalidateAll();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMenu(Long id) {
        Menu existed = baseMapper.selectById(id);
        if (existed == null) {
            throw new BizException(ResultCode.NOT_FOUND, "菜单不存在");
        }
        Long childCount = baseMapper.selectCount(new LambdaQueryWrapper<Menu>()
                .eq(Menu::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "存在子菜单，禁止删除");
        }
        // 同步清理角色-菜单授权关系，避免残留脏数据
        roleMenuMapper.delete(new LambdaQueryWrapper<RoleMenu>().eq(RoleMenu::getMenuId, id));
        baseMapper.deleteById(id);
        // 菜单变更清空缓存
        menuCache.invalidateAll();
    }

    @Override
    public List<Long> listMenuRoles(LoginUser current, Long menuId) {
        requireMenuExists(menuId);
        Set<Long> tenantRoleIds = tenantRoleIds(current);
        return roleMenuMapper.selectList(new LambdaQueryWrapper<RoleMenu>()
                        .eq(RoleMenu::getMenuId, menuId))
                .stream().map(RoleMenu::getRoleId)
                .filter(tenantRoleIds::contains)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveMenuRoles(LoginUser current, Long menuId, List<Long> roleIds) {
        Menu menu = requireMenuExists(menuId);
        // 租户专属菜单（perms 前缀 system:tenant）仅平台管理员可授权给角色，
        // 与角色授权侧的静默过滤互补：这里是对单个菜单的显式操作，直接拒绝更明确
        if (!current.isAdmin() && menu.getPerms() != null && menu.getPerms().startsWith("system:tenant")) {
            throw new BizException(ResultCode.FORBIDDEN, "租户专属菜单仅平台管理员可授权");
        }
        Set<Long> tenantRoleIds = tenantRoleIds(current);
        // 逐个校验角色归属，防止把本租户菜单授权给别租户角色（越权写入）
        if (roleIds != null) {
            for (Long roleId : roleIds) {
                if (!tenantRoleIds.contains(roleId)) {
                    throw new BizException(ResultCode.PARAM_ERROR, "角色不存在或不属于当前租户");
                }
            }
        }
        // 批量删除本租户角色与该菜单的关联（避免循环逐条删除）
        List<RoleMenu> existedRelations = roleMenuMapper.selectList(new LambdaQueryWrapper<RoleMenu>()
                .eq(RoleMenu::getMenuId, menuId));
        List<Long> roleIdsToDelete = existedRelations.stream()
                .filter(rm -> tenantRoleIds.contains(rm.getRoleId()))
                .map(RoleMenu::getRoleId)
                .toList();
        if (!roleIdsToDelete.isEmpty()) {
            roleMenuMapper.delete(new LambdaQueryWrapper<RoleMenu>()
                    .eq(RoleMenu::getMenuId, menuId)
                    .in(RoleMenu::getRoleId, roleIdsToDelete));
        }
        // 批量插入新关联
        if (roleIds != null) {
            for (Long roleId : roleIds) {
                RoleMenu rm = new RoleMenu();
                rm.setMenuId(menuId);
                rm.setRoleId(roleId);
                roleMenuMapper.insert(rm);
            }
        }
        log.info("菜单关联角色已保存: menuId={}, count={}, operator={}",
                menuId, roleIds == null ? 0 : roleIds.size(), current.getUsername());
    }

    // ------------------------------------------------------------
    // 内部辅助
    // ------------------------------------------------------------

    /** 校验菜单基础字段（类型枚举 + 名称非空） */
    private void validateMenu(Menu menu) {
        if (menu.getMenuName() == null || menu.getMenuName().isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "菜单名称不能为空");
        }
        String type = menu.getMenuType();
        if (!Menu.TYPE_DIR.equals(type) && !Menu.TYPE_MENU.equals(type) && !Menu.TYPE_BUTTON.equals(type)) {
            throw new BizException(ResultCode.PARAM_ERROR, "菜单类型仅支持 M 目录 / C 菜单 / F 按钮");
        }
    }

    /** 菜单存在性校验（返回菜单实体供调用方复用，避免二次查库） */
    private Menu requireMenuExists(Long menuId) {
        Menu menu = baseMapper.selectById(menuId);
        if (menu == null) {
            throw new BizException(ResultCode.NOT_FOUND, "菜单不存在");
        }
        return menu;
    }

    /** 当前用户租户内的角色ID集合（关联关系按此收窄，防跨租户读写） */
    private Set<Long> tenantRoleIds(LoginUser current) {
        return roleMapper.selectList(new LambdaQueryWrapper<Role>()
                        .eq(Role::getTenantId, current.getTenantId()))
                .stream().map(Role::getId).collect(Collectors.toSet());
    }
}
