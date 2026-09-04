package com.claw.agent.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.claw.agent.model.LoginLog;
import com.claw.agent.security.LoginUser;

/**
 * 登录日志查询接口（写入由认证服务在登录/登出流程完成）。
 */
public interface LoginLogService extends IService<LoginLog> {

    /**
     * 登录日志分页（按时间倒序）：平台管理员看全部，租户管理员看本租户，支持搜索与筛选。
     *
     * @param current   当前登录用户
     * @param pageNum   页码（从 1 起，越界自动收敛）
     * @param pageSize  每页条数（1~100，越界自动收敛）
     * @param keyword   关键词（模糊匹配用户名，可为空）
     * @param eventType 事件类型（LOGIN/LOGOUT，可为空）
     * @param status    状态（1 成功 / 0 失败，可为空）
     * @return 分页结果
     */
    IPage<LoginLog> pageLogs(LoginUser current, long pageNum, long pageSize,
                              String keyword, String eventType, Integer status);
}
