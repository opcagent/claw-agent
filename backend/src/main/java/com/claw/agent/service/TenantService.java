package com.claw.agent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.claw.agent.model.Tenant;
import com.claw.agent.model.dto.SetAdminRequest;
import com.claw.agent.model.dto.TenantCreateWithAdminRequest;
import com.claw.agent.security.LoginUser;

import java.util.List;

/**
 * 租户管理服务接口（平台级）。
 * <p>
 * 继承 {@link IService} 获得 MyBatis Plus 通用 CRUD 能力；
 * 业务方法（编码唯一、有用户禁删）声明如下，
 * 实现见 {@code impl/TenantServiceImpl}。
 */
public interface TenantService extends IService<Tenant> {

    /**
     * 全部租户列表（按ID升序）。
     *
     * @return 租户列表
     */
    List<Tenant> listTenants();

    /**
     * 新增租户（编码唯一校验，默认启用）。
     *
     * @param current 操作人（仅用于审计日志）
     * @param tenant  租户信息
     */
    void addTenant(LoginUser current, Tenant tenant);

    /**
     * 修改租户（编码不可改，避免历史数据失联）。
     *
     * @param id     租户ID
     * @param tenant 更新内容
     */
    void updateTenant(Long id, Tenant tenant);

    /**
     * 删除租户（存在用户时禁止删除）。
     *
     * @param id 租户ID
     */
    void deleteTenant(Long id);

    /**
     * 设置租户管理员（平台管理员专用）。
     * <p>
     * 业务规则：
     * <ul>
     *   <li>检查租户是否存在</li>
     *   <li>检查用户是否属于该租户</li>
     *   <li>给用户分配该租户的 tenant_admin 角色（全量替换，仅保留此角色）</li>
     *   <li>记录审计日志</li>
     * </ul>
     *
     * @param current 操作人（必须为平台管理员）
     * @param tenantId 租户ID
     * @param request  请求参数（包含 userId）
     */
    void setTenantAdmin(LoginUser current, Long tenantId, SetAdminRequest request);

    /**
     * 新增租户并创建初始管理员（推荐方式）。
     * <p>
     * 业务规则：
     * <ul>
     *   <li>创建租户（编码唯一校验）</li>
     *   <li>如果提供了 adminUsername，则创建用户并授予 tenant_admin 角色</li>
     *   <li>用户名全局唯一校验</li>
     *   <li>密码 BCrypt 加密存储</li>
     *   <li>事务保证原子性：租户、部门、角色、用户、角色关联要么全部成功，要么全部回滚</li>
     * </ul>
     *
     * @param current 操作人（必须为平台管理员）
     * @param request 租户及管理员信息
     */
    void addTenantWithAdmin(LoginUser current, TenantCreateWithAdminRequest request);
}
