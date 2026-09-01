package com.claw.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.claw.agent.model.Role;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 角色数据访问层。
 * <p>
 * 提供按用户聚合角色的联表查询（sys_user_role 关联），
 * 供登录认证时把 roleKey 集合写入 JWT。
 */
public interface RoleMapper extends BaseMapper<Role> {

    /**
     * 查询用户的全部启用角色（经 sys_user_role 关联）。
     *
     * @param userId 用户ID
     * @return 角色列表（按显示顺序）
     */
    List<Role> selectRolesByUserId(@Param("userId") String userId);
}
