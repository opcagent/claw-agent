package com.claw.agent.service;

import com.claw.agent.model.dto.OnlineUserVO;
import com.claw.agent.security.LoginUser;

import java.util.List;

/**
 * 在线用户监控服务：读取活跃快照并补全展示字段。
 */
public interface MonitorService {

    /**
     * 查询当前活跃用户列表（保留窗口内，按最近活跃时间倒序）。
     * <p>
     * 数据可见性：平台管理员看全部租户；租户管理员仅看本租户。
     *
     * @param viewer 当前登录的管理员
     * @return 在线用户视图列表
     */
    List<OnlineUserVO> listOnlineUsers(LoginUser viewer);
}
