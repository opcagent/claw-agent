package com.claw.agent.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务操作日志实体（表 sys_oper_log）。
 * <p>
 * 由 {@code ReactiveSupport} 在管理端增删改/授权操作执行后统一记录，
 * 成功失败均落库（失败记错误信息），供系统管理-日志页面查询。
 */
@Data
@TableName("sys_oper_log")
public class OperLog {

    /** 日志ID（主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作人所属租户ID */
    private Long tenantId;

    /** 功能模块（如 用户管理 / 菜单管理） */
    private String module;

    /** 操作类型（OperType 枚举名：CREATE/UPDATE/DELETE/GRANT/OTHER） */
    private String operType;

    /** 操作描述（接口用途简述） */
    private String operDesc;

    /** 状态：1 成功 / 0 失败 */
    private Integer status;

    /** 失败原因（成功时为空） */
    private String errorMsg;

    /** 操作人用户名 */
    private String operName;

    /** 访问者 IP（由 ClientIpFilter 解析，代理场景优先取 X-Forwarded-For 首段） */
    private String ip;

    /** 操作时间 */
    private LocalDateTime operTime;
}
