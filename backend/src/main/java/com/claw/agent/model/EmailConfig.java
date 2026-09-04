package com.claw.agent.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户邮箱配置实体。
 * <p>
 * 存储用户的 SMTP 邮箱服务器配置,用于 Agent 发送邮件通知。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("email_config")
public class EmailConfig extends BaseEntity {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID（格式：租户编码_自增序号） */
    private String userId;

    /** 租户ID */
    private Long tenantId;

    // ==================== SMTP 服务器配置 ====================

    /** SMTP 服务器地址 (如 smtp.gmail.com) */
    private String smtpHost;

    /** SMTP 端口 (25/465/587) */
    private Integer smtpPort;

    /** 发件人邮箱账号 */
    private String smtpUsername;

    /** 邮箱授权码/密码 (加密存储) */
    private String smtpPassword;

    /** 是否使用 SSL */
    private Boolean smtpUseSsl;

    /** 是否使用 TLS */
    private Boolean smtpUseTls;

    // ==================== 发件人信息 ====================

    /** 发件人显示名称 */
    private String fromName;

    /** 发件人邮箱地址 */
    private String fromEmail;

    // ==================== 状态与元数据 ====================

    /** 是否启用 */
    private Boolean enabled;

    /** 是否为默认配置 */
    @TableField("is_default")
    private Boolean defaultFlag;

    /** 备注说明 */
    private String remark;
}
