package com.claw.agent.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 审计字段基类：所有业务实体统一继承。
 * <p>
 * 六个字段全部由 MyBatis Plus {@code MetaObjectHandler} 自动填充，
 * 业务代码禁止手工 set（创建人/修改人及其 ID 取自 {@code UserContextHolder}）；
 * 操作人 ID 供后续按人关联查询，用户名冗余保留便于直接展示。
 */
@Getter
@Setter
public abstract class BaseEntity {

    /** 创建时间（插入时填充） */
    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间（插入与更新均填充） */
    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 创建人用户名（插入时填充） */
    @Schema(description = "创建人")
    @TableField(fill = FieldFill.INSERT)
    private String creator;

    /** 修改人用户名（插入与更新均填充） */
    @Schema(description = "修改人")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updater;

    /** 创建人用户ID（插入时填充；旧版 token 无 userId 声明时为空） */
    @Schema(description = "创建人ID")
    @TableField(fill = FieldFill.INSERT)
    private String creatorId;

    /** 修改人用户ID（插入与更新均填充；旧版 token 无 userId 声明时为空） */
    @Schema(description = "修改人ID")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updaterId;
}
