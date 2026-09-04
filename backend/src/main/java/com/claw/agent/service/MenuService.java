package com.claw.agent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.claw.agent.model.Menu;
import com.claw.agent.security.LoginUser;

import java.util.List;

/**
 * 菜单/权限点管理服务接口。
 * <p>
 * 继承 {@link IService} 获得 MyBatis Plus 通用 CRUD 能力；
 * 业务方法（类型校验、防环、子菜单禁删、菜单↔角色关联）声明如下，
 * 实现见 {@code impl/MenuServiceImpl}。
 */
public interface MenuService extends IService<Menu> {

    /**
     * 启用菜单列表（扁平，按 orderNum 升序，前端按 parentId 组树）。
     *
     * @return 菜单列表
     */
    List<Menu> listEnabledMenus();

    /**
     * 新增菜单/按钮（父菜单必须存在，类型限定 M / C / F）。
     *
     * @param menu 菜单信息
     */
    void addMenu(Menu menu);

    /**
     * 修改菜单（父菜单不可指向自身或子孙，避免树成环）。
     *
     * @param id   菜单ID
     * @param menu 更新内容
     */
    void updateMenu(Long id, Menu menu);

    /**
     * 删除菜单（存在子菜单时禁止；同步清理角色-菜单授权）。
     *
     * @param id 菜单ID
     */
    void deleteMenu(Long id);

    /**
     * 查询菜单已关联的角色ID列表（仅当前用户租户内的角色）。
     *
     * @param current 当前登录用户
     * @param menuId  菜单ID
     * @return 角色ID列表
     */
    List<Long> listMenuRoles(LoginUser current, Long menuId);

    /**
     * 保存菜单关联角色（仅替换当前用户租户内角色的关联记录，全量替换）。
     *
     * @param current 当前登录用户
     * @param menuId  菜单ID
     * @param roleIds 角色ID列表（可为空表示解除本租户全部关联）
     */
    void saveMenuRoles(LoginUser current, Long menuId, List<Long> roleIds);

    /**
     * 根据租户功能配置过滤菜单列表。
     * <p>
     * 如果租户没有配置记录，返回原始菜单列表（向后兼容）。
     *
     * @param tenantId 租户ID
     * @param menus    原始菜单列表
     * @return 过滤后的菜单列表
     */
    List<Menu> filterMenusByTenant(Long tenantId, List<Menu> menus);
}
