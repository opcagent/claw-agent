package com.claw.agent.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Token 使用汇总实体。
 * <p>
 * 按用户+周期(月)汇总 Token 使用量,用于快速查询统计。
 */
@Data
@TableName("token_usage_summary")
public class TokenUsageSummary {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID（格式：租户编码_自增序号） */
    private String userId;

    /** 租户ID */
    private Long tenantId;

    /** 用户名 */
    private String username;

    /** 周期类型: daily/monthly/yearly */
    private String periodType;

    /** 周期开始日期 */
    private LocalDate periodStart;

    /** 周期结束日期 */
    private LocalDate periodEnd;

    /** 累计提示词 Token */
    private Long totalPromptTokens;

    /** 累计回复 Token */
    private Long totalCompletionTokens;

    /** 累计总 Token */
    private Long totalTokens;

    /** 请求次数 */
    private Integer requestCount;

    /** 最后更新时间 */
    private LocalDateTime lastUpdateTime;
}
