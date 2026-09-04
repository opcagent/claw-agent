package com.claw.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.claw.agent.model.User;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户数据访问层。
 * <p>
 * 内置 CRUD 由 MyBatis Plus BaseMapper 提供；
 * 涉及 sys_user_tenant 联表的组织/部门维度查询在 {@code mapper/UserMapper.xml} 中定义。
 */
public interface UserMapper extends BaseMapper<User> {

    /**
     * 按组织查询用户列表（经 sys_user_tenant 关联）。
     *
     * @param tenantId 组织ID
     * @param limit    最大条数
     * @return 用户列表
     */
    List<User> selectUsersByTenantId(@Param("tenantId") Long tenantId, @Param("limit") int limit);

    /**
     * 按组织分页查询用户（支持关键词模糊 + 状态筛选）。
     *
     * @param page    分页参数
     * @param tenantId 组织ID
     * @param keyword  关键词（可为 null）
     * @param status   状态（可为 null）
     * @return 分页结果
     */
    IPage<User> pageUsersByTenantId(Page<User> page, @Param("tenantId") Long tenantId,
                                     @Param("keyword") String keyword, @Param("status") Integer status);

    /**
     * 按组织+部门分页查询用户（支持关键词模糊 + 状态筛选）。
     *
     * @param page    分页参数
     * @param tenantId 组织ID
     * @param deptId   部门ID
     * @param keyword  关键词（可为 null）
     * @param status   状态（可为 null）
     * @return 分页结果
     */
    IPage<User> pageUsersByTenantIdAndDeptId(Page<User> page, @Param("tenantId") Long tenantId,
                                              @Param("deptId") Long deptId, @Param("keyword") String keyword,
                                              @Param("status") Integer status);

    /**
     * 按部门统计用户数（删除部门前校验）。
     *
     * @param deptId 部门ID
     * @return 用户数
     */
    Long countUsersByDeptId(@Param("deptId") Long deptId);

    /**
     * 查组织内最大序号用户（生成规则ID用，按 ID 倒序取第一条）。
     *
     * @param tenantId 组织ID
     * @return 最后一个用户（可能为 null）
     */
    User selectLastUserByTenantId(@Param("tenantId") Long tenantId);
}
