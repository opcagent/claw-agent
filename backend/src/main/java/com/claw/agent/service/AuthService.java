package com.claw.agent.service;

import com.claw.agent.model.LoginLog;
import com.claw.agent.model.Menu;
import com.claw.agent.model.dto.ChangePasswordRequest;
import com.claw.agent.model.dto.LoginRequest;
import com.claw.agent.model.dto.LoginResponse;
import com.claw.agent.model.dto.ProfileResponse;
import com.claw.agent.model.dto.ProfileUpdateRequest;
import com.claw.agent.model.dto.SwitchTenantRequest;
import com.claw.agent.model.dto.TenantBrief;
import com.claw.agent.security.LoginUser;

import java.util.List;

/**
 * 认证业务接口：登录 / 修改密码 / 登出 / 当前用户信息与可见菜单。
 * <p>
 * 登录成功失败与登出均落 {@code sys_login_log}，供日志管理页查询；
 * 账号由管理员创建，不提供自助注册。
 */
public interface AuthService {

    /**
     * 登录：校验账号密码，签发 JWT，下发角色与权限，并记录登录日志。
     *
     * @param request 登录请求（用户名 + 密码）
     * @return 登录响应（token / 角色 / 权限）
     */
    LoginResponse login(LoginRequest request);

    /**
     * 修改本人登录密码：核验原密码后替换为新密码（BCrypt）。
     *
     * @param current 当前登录用户（操作对象取自 JWT，防越权）
     * @param request 原密码 + 新密码
     */
    void changePassword(LoginUser current, ChangePasswordRequest request);

    /**
     * 登出：记录登出日志（JWT 无状态，前端负责丢弃 token）。
     *
     * @param current 当前登录用户
     */
    void logout(LoginUser current);

    /**
     * 当前登录用户信息回显（刷新页面时重新聚合权限）。
     *
     * @param current 当前登录用户
     * @return 用户信息（无 token）
     */
    LoginResponse currentUserInfo(LoginUser current);

    /**
     * 当前用户可见菜单树（目录 + 菜单，不含按钮），按角色授权聚合，
     * 驱动前端导航渲染；平台管理员短路返回全部启用菜单。
     *
     * @param current 当前登录用户
     * @return 菜单列表（按 parent_id + order_num 排序）
     */
    List<Menu> listMyMenus(LoginUser current);

    /**
     * 本人个人信息详情：基础资料 + 最近一次成功登录（时间 / IP）。
     *
     * @param current 当前登录用户（查询对象取自 JWT，防越权）
     * @return 个人信息详情
     */
    ProfileResponse profile(LoginUser current);

    /**
     * 本人资料自助更新：仅昵称与联系方式（手机/邮箱/性别），
     * 格式校验下沉用户服务；用户名等身份字段不可变更。
     *
     * @param current 当前登录用户（操作对象取自 JWT，防越权）
     * @param request 资料更新请求
     */
    void updateProfile(LoginUser current, ProfileUpdateRequest request);

    /**
     * 本人最近登录记录（仅登录事件，时间倒序），条数服务端限幅。
     *
     * @param current 当前登录用户
     * @param limit   期望条数（实际限幅 1～50）
     * @return 登录日志列表（含失败记录，便于发现异常登录）
     */
    List<LoginLog> myLoginLogs(LoginUser current, int limit);

    /**
     * 切换组织：已登录用户切换到另一个已加入的组织，重新签发 JWT。
     * <p>
     * 切换后当前活跃组织变更，工作区和配置按新组织解析。
     *
     * @param current 当前登录用户
     * @param request 切换组织请求（目标租户ID）
     * @return 登录响应（含新 token / 角色 / 权限）
     */
    LoginResponse switchTenant(LoginUser current, SwitchTenantRequest request);

    /**
     * 当前用户可登录的组织列表：已登录用户切换组织时，前端需要知道可选的组织。
     * <p>
     * 平台管理员（不属于任何组织）返回空列表。
     *
     * @param current 当前登录用户
     * @return 组织简要列表（含角色键、是否默认）
     */
    List<TenantBrief> listMyTenants(LoginUser current);
}
