package com.claw.agent.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属租户ID（0 为平台公共字典） */
    private Long tenantId;

    /** 字典类型（关联 sys_dict_type.dict_type） */
    private String dictType;

    /** 字典标签（展示文案） */
    private String dictLabel;

    /** 字典键值 */
    private String dictValue;

    /** 显示顺序 */
    private Integer dictSort;

    /** 样式属性（前端标签色，如 success / danger） */
    private String cssClass;

    /** 是否默认：1 是 / 0 否 */
    @TableField("is_default")
    private Integer defaultFlag;

    /** 状态：1 启用 / 0 禁用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
