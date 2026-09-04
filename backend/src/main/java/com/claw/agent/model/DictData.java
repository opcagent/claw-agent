package com.claw.agent.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典数据实体（表 sys_dict_data，若依风格）。
 * <p>
 * 一条 = 一个字典键值对；前端下拉/标签渲染按 (tenant_id, dict_type) 拉取，
 * css_class 控制标签颜色，默认租户优先取平台字典（tenant_id=0）。
 */
@Data
@EqualsAndHashCode(callSuper=false)
@TableName("sys_dict_data")
public class DictData extends BaseEntity {

    /** 主键 */
    @Schema(description = "主键")
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属租户ID（0 为平台公共字典） */
    @Schema(description = "租户ID")
    private Long tenantId;

    /** 字典类型（关联 sys_dict_type.dict_type） */
    @Schema(description = "字典类型")
    private String dictType;

    /** 字典标签（展示文案） */
    @Schema(description = "字典标签")
    private String dictLabel;

    /** 字典键值 */
    @Schema(description = "字典键值")
    private String dictValue;

    /** 显示顺序 */
    @Schema(description = "显示顺序")
    private Integer dictSort;

    /** 样式属性（前端标签色，如 success / danger） */
    @Schema(description = "标签样式")
    private String cssClass;

    /** 是否默认：1 是 / 0 否 */
    @Schema(description = "默认：1是/0否")
    @TableField("is_default")
    private Integer defaultFlag;

    /** 状态：1 启用 / 0 禁用 */
    @Schema(description = "状态：1启用/0禁用")
    private Integer status;

    /** 备注 */
    @Schema(description = "备注")
    private String remark;
}
