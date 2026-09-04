package com.claw.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.claw.agent.model.Menu;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 菜单/权限数据访问层。
 * <p>
 * 提供按用户聚合权限标识（perms）的三级联表查询：
 * sys_user_tenant -> sys_role_menu -> sys_menu，
 * 登录后下发前端用于按钮级显隐控制。
 */
public interface MenuMapper extends BaseMapper<Menu> {

    /**
     * 查询用户在指定组织内的权限标识。
     *
     * @param userId   用户ID
     * @param tenantId 组织ID
     * @return 权限标识列表（去重）
     */
    List<String> selectPermsByUserIdAndTenantId(@Param("userId") String userId,
                                                 @Param("tenantId") Long tenantId);

    /**
     * 查询用户在指定组织内可见的菜单（目录/菜单，不含按钮）。
     * <p>多角色场景下仅返回当前活跃组织的菜单，避免跨组织菜单泄露。
     *
     * @param userId   用户ID
     * @param tenantId 组织ID
     * @return 菜单列表（按显示顺序）
     */
    List<Menu> selectMenusByUserIdAndTenantId(@Param("userId") String userId,
                                               @Param("tenantId") Long tenantId);
}
