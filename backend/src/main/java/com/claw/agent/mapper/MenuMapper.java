package com.claw.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.claw.agent.model.Menu;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 菜单/权限数据访问层。
 * <p>
 * 提供按用户聚合权限标识（perms）的三级联表查询：
 * sys_user_role -> sys_role_menu -> sys_menu，
 * 登录后下发前端用于按钮级显隐控制。
 */
public interface MenuMapper extends BaseMapper<Menu> {

    /**
     * 查询用户的全部启用权限标识（如 system:user:add）。
     * <p>
     * 平台管理员角色（role_key=admin）视为拥有全部权限，由 service 层短路处理。
     *
     * @param userId 用户ID
     * @return 权限标识列表（去重由 SQL DISTINCT 保证）
     */
    List<String> selectPermsByUserId(@Param("userId") String userId);

    /**
     * 查询用户可见的菜单（目录/菜单，不含按钮），用于登录后构建前端路由。
     *
     * @param userId 用户ID
     * @return 菜单列表（按显示顺序）
     */
    List<Menu> selectMenusByUserId(@Param("userId") String userId);
}
