package com.claw.agent.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 定时任务执行日志实体（对应数据库表 scheduled_task_log）。
 * <p>
 * 记录每次定时任务的执行结果，包括状态、结果摘要、错误信息等，
 * 便于排查问题和审计追溯。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("scheduled_task_log")
public class ScheduledTaskLog extends BaseEntity {

    /** 主键（数据库自增） */
    @Schema(description = "主键")
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联任务ID */
    @Schema(description = "任务ID")
    private Long taskId;

    /** 所属租户ID */
    @Schema(description = "租户ID")
    private Long tenantId;

    /** 执行状态：SUCCESS / FAIL */
    @Schema(description = "执行状态")
    private String status;

    /** 执行结果摘要 */
    @Schema(description = "结果摘要")
    private String resultText;

    /** 执行时间 */
    @Schema(description = "执行时间")
    private java.time.LocalDateTime runTime;

    /** 错误信息（失败时） */
    @Schema(description = "错误信息")
    private String errorMsg;
}
