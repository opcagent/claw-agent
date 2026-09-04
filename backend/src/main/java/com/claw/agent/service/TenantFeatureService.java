package com.claw.agent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.claw.agent.model.TenantFeature;
import com.claw.agent.security.LoginUser;

import java.util.List;

/**
 * 租户功能模块配置服务接口。
 * <p>
 * 平台管理员可为每个租户配置可用的功能模块（菜单），
 * 租户管理员只能使用平台管理员配置的功能模块。
 */
public interface TenantFeatureService extends IService<TenantFeature> {

    /**
     * 查询租户的功能配置列表（返回菜单ID列表）。
     * <p>
     * 如果租户没有配置记录，返回 null 表示拥有全部功能（向后兼容）。
     *
     * @param tenantId 租户ID
     * @return 启用的菜单ID列表（null 表示全部启用）
     */
    List<Long> getTenantFeatureMenuIds(Long tenantId);

    /**
     * 保存租户的功能配置（全量替换）。
     *
     * @param current 当前登录用户（必须为平台管理员）
     * @param tenantId 租户ID
     * @param menuIds  启用的菜单ID列表
     */
    void saveTenantFeatures(LoginUser current, Long tenantId, List<Long> menuIds);

    /**
     * 根据租户功能配置过滤菜单列表。
     * <p>
     * 如果租户没有配置记录，返回原始菜单列表（向后兼容）。
     *
     * @param tenantId 租户ID
     * @param menus    原始菜单列表
     * @return 过滤后的菜单列表
     */
    List<com.claw.agent.model.Menu> filterMenusByTenant(Long tenantId, List<com.claw.agent.model.Menu> menus);
}
