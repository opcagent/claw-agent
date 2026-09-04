package com.claw.agent.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户常用语/快捷指令实体（对应数据库表 user_quick_phrase）。
 * <p>
 * 用户可预存常用 prompt 片段，聊天时通过「/」快捷面板一键填入，
 * 减少重复输入；按 sort_order 排序，仅本人可见。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_quick_phrase")
public class UserQuickPhrase extends BaseEntity {

    /** 主键（数据库自增） */
    @Schema(description = "主键")
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属租户ID */
    @Schema(description = "租户ID")
    private Long tenantId;

    /** 所属用户ID */
    @Schema(description = "用户ID")
    private String userId;

    /** 所属用户名（冗余，便于审计） */
    @Schema(description = "用户名")
    private String username;

    /** 快捷指令标题 */
    @Schema(description = "标题")
    private String title;

    /** 发送内容 */
    @Schema(description = "内容")
    private String content;

    /** 排序序号（越小越靠前） */
    @Schema(description = "排序序号")
    private Integer sortOrder;
}
