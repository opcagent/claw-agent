package com.claw.agent.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.claw.agent.model.User;
import com.claw.agent.model.dto.UserCreateRequest;
import com.claw.agent.security.LoginUser;

import java.util.List;

/**
 * 用户管理服务接口（租户内）。
 * <p>
 * 继承 {@link IService} 获得 MyBatis Plus 通用 CRUD 能力；
 * 业务方法（租户隔离、角色分配、防跨租户提权）声明如下，
 * 实现见 {@code impl/UserServiceImpl}。
 */
public interface UserService extends IService<User> {

    /**
     * 本租户用户列表（密码字段置空不下发）。
     *
     * @param current 当前登录用户
     * @return 用户列表（密码已置空）
     */
    List<User> listUsers(LoginUser current);

    /**
     * 按指定租户查询用户列表（密码字段置空不下发），仅平台管理员可跨租户查询。
     *
     * @param current  当前登录用户
     * @param tenantId 目标租户 ID
     * @return 用户列表（密码已置空）
     */
    List<User> listUsersByTenant(LoginUser current, Long tenantId);

    /**
     * 本租户用户分页（密码字段置空不下发），支持关键词搜索与状态/部门筛选。
     *
     * @param current  当前登录用户
     * @param pageNum  页码（从 1 起，越界自动收敛）
     * @param pageSize 每页条数（1~100，越界自动收敛）
     * @param keyword  关键词（模糊匹配用户名/昵称/手机/邮箱，可为空）
     * @param status   状态筛选（1 启用 / 0 禁用，可为空表示不过滤）
     * @param deptId   部门筛选（可为空表示不过滤）
     * @return 分页结果（密码已置空）
     */
    IPage<User> pageUsers(LoginUser current, long pageNum, long pageSize,
                           String keyword, Integer status, Long deptId);

    /**
     * 新增用户（默认启用，密码服务端加密，用户名全局唯一）。
     *
     * @param current 当前登录用户（决定租户归属）
     * @param request 新增请求
     */
    void addUser(LoginUser current, UserCreateRequest request);

    /**
     * 更新用户基础信息（昵称/部门/状态/备注）。
     *
     * @param current 当前登录用户
     * @param id      目标用户ID
     * @param user    更新内容
     */
    void updateUser(LoginUser current, String id, User user);

    /**
     * 重置密码（明文入参，服务端 BCrypt 加密）。
     *
     * @param current     当前登录用户
     * @param id          目标用户ID
     * @param newPassword 新密码明文
     */
    void resetPassword(LoginUser current, String id, String newPassword);

    /**
     * 删除用户（禁止删除当前登录账号，同步清理角色关联）。
     *
     * @param current 当前登录用户
     * @param id      目标用户ID
     */
    void deleteUser(LoginUser current, String id);

    /**
     * 查询用户已分配的角色ID列表。
     *
     * @param current 当前登录用户
     * @param id      目标用户ID
     * @return 角色ID列表
     */
    List<Long> listUserRoles(LoginUser current, String id);

    /**
     * 保存用户角色分配（全量替换；仅允许分配本租户角色，防跨租户提权）。
     *
     * @param current 当前登录用户
     * @param id      目标用户ID
     * @param roleIds 角色ID列表（可为空表示清空）
     */
    void saveUserRoles(LoginUser current, String id, List<Long> roleIds);

    /**
     * 本人资料自助更新：昵称与联系方式（手机/邮箱/性别），
     * 供个人中心调用；格式校验与 updateUser 同源。
     *
     * @param current  当前登录用户（操作对象即本人）
     * @param nickname 昵称
     * @param phone    手机号（可空）
     * @param email    邮箱（可空）
     * @param gender   性别（0/1/2，可空缺省 0）
     */
    void updateMyProfile(LoginUser current, String nickname, String phone, String email, Integer gender);
}
