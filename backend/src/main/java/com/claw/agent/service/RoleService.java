package com.claw.agent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.claw.agent.model.Role;
import com.claw.agent.security.LoginUser;

import java.util.List;

/**
 * 角色管理服务接口（租户内）。
 * <p>
 * 继承 {@link IService} 获得 MyBatis Plus 通用 CRUD 能力；
 * 业务方法（roleKey 唯一、删除保护、菜单授权全量替换）声明如下，
 * 实现见 {@code impl/RoleServiceImpl}。
 */
public interface RoleService extends IService<Role> {

    /**
     * 本租户角色列表（按显示顺序）。
     *
     * @param current 当前登录用户（决定租户）
     * @return 角色列表
     */
    List<Role> listRoles(LoginUser current);

    /**
     * 新增角色（roleKey 租户内唯一，默认数据权限为「仅本人」）。
     *
     * @param current 当前登录用户（决定租户）
     * @param role    角色信息
     */
    void addRole(LoginUser current, Role role);

    /**
     * 修改角色（roleKey 不可改，避免已签发 JWT 与授权关系失联）。
     *
     * @param current 当前登录用户
     * @param id      角色ID
     * @param role    更新内容
     */
    void updateRole(LoginUser current, Long id, Role role);

    /**
     * 删除角色（已分配用户时禁止，同步清理角色-菜单授权）。
     *
     * @param current 当前登录用户
     * @param id      角色ID
     */
    void deleteRole(LoginUser current, Long id);

    /**
     * 查询角色已授权的菜单ID列表（供前端授权对话框回显勾选）。
     *
     * @param current 当前登录用户
     * @param id      角色ID
     * @return 菜单ID列表
     */
    List<Long> listRoleMenus(LoginUser current, Long id);

    /**
     * 保存角色菜单授权（全量替换：先清空再插入）。
     *
     * @param current 当前登录用户
     * @param id      角色ID
     * @param menuIds 菜单ID列表（可为空表示清空授权）
     */
    void saveRoleMenus(LoginUser current, Long id, List<Long> menuIds);
}
