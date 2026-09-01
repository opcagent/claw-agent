package com.claw.agent.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 定时任务实体（对应数据库表 scheduled_task）。
 * <p>
 * 用户可配置定时任务，按 Cron 表达式自动触发 Agent 对话，
 * 执行结果可选邮件通知。适用于「每日早报」「周报汇总」等自动化场景。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("scheduled_task")
public class ScheduledTask extends BaseEntity {

    /** 执行状态：成功 */
    public static final String STATUS_SUCCESS = "SUCCESS";
    /** 执行状态：失败 */
    public static final String STATUS_FAIL = "FAIL";

    /** 主键（数据库自增） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属租户ID */
    private Long tenantId;

    /** 所属用户ID */
    private String userId;

    /** 所属用户名（冗余） */
    private String username;

    /** 任务名称 */
    private String taskName;

    /** Cron 表达式 */
    private String cronExpr;

    /** 预设模板编码（可选） */
    private String presetCode;

    /** 流水线编码（可选） */
    private String pipelineCode;

    /** 发送给 Agent 的消息内容 */
    private String promptContent;

    /** 结果通知邮箱（为空不通知） */
    private String notifyEmail;

    /** 是否启用：1 启用 / 0 禁用 */
    private Integer enabled;

    /** 上次执行时间 */
    private java.time.LocalDateTime lastRunTime;

    /** 下次执行时间 */
    private java.time.LocalDateTime nextRunTime;
}
