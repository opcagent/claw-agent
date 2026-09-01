package com.claw.agent.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录日志实体（表 sys_login_log）。
 * <p>
 * 与业务操作日志分表存储：记录登录成功/失败与登出事件，
 * 由认证服务在登录/登出流程中写入。
 */
@Data
@TableName("sys_login_log")
public class LoginLog {

    /** 事件类型：登录 */
    public static final String TYPE_LOGIN = "LOGIN";

    /** 事件类型：登出 */
    public static final String TYPE_LOGOUT = "LOGOUT";

    /** 日志ID（主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户名（登录失败时也记录） */
    private String username;

    /** 用户所属租户ID（登录失败/用户不存在时为空） */
    private Long tenantId;

    /** 事件类型：LOGIN / LOGOUT */
    private String eventType;

    /** 状态：1 成功 / 0 失败 */
    private Integer status;

    /** 提示信息（失败原因等） */
    private String msg;

    /** 访问者 IP（由 ClientIpFilter 解析，代理场景优先取 X-Forwarded-For 首段） */
    private String ip;

    /** 事件时间 */
    private LocalDateTime loginTime;
}
