package com.claw.agent.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Token 使用流水实体。
 * <p>
 * 记录每次模型调用的详细 Token 消耗,用于计费和统计分析。
 */
@Data
@TableName("token_usage_log")
public class TokenUsageLog {

    /** 主键 */
    @Schema(description = "主键")
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID（格式：租户编码_自增序号） */
    @Schema(description = "用户ID")
    private String userId;

    /** 租户ID */
    @Schema(description = "租户ID")
    private Long tenantId;

    /** 用户名 */
    @Schema(description = "用户名")
    private String username;

    /** 会话ID */
    @Schema(description = "会话ID")
    private String sessionId;

    /** 模型提供商: openai/dashscope/ollama */
    @Schema(description = "模型提供商")
    private String provider;

    /** 模型名称 */
    @Schema(description = "模型名称")
    private String modelName;

    /** 提示词 Token 数 */
    @Schema(description = "输入Token数")
    private Integer promptTokens;

    /** 回复 Token 数 */
    @Schema(description = "输出Token数")
    private Integer completionTokens;

    /** 总 Token 数 */
    @Schema(description = "总Token数")
    private Integer totalTokens;

    /** 请求ID(用于追踪) */
    @Schema(description = "请求ID")
    private String requestId;

    /** 使用的工具名称(如果有) */
    @Schema(description = "工具名称")
    private String toolName;

    /** 使用时间 */
    @Schema(description = "使用时间")
    private LocalDateTime usageTime;

    /** 使用日期(计算字段,MyBatis Plus 不映射) */
    // private LocalDate usageDate;

    /** 创建时间 */
    private LocalDateTime createTime;
}
