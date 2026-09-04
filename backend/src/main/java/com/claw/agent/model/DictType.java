package com.claw.agent.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典类型实体（表 sys_dict_type，若依风格）。
 * <p>
 * 平台公共字典 tenant_id=0；租户可扩展自己的字典（同租户内 dict_type 唯一）。
 */
@Data
@EqualsAndHashCode(callSuper=false)
@TableName("sys_dict_type")
public class DictType extends BaseEntity {

    /** 主键 */
    @Schema(description = "主键")
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属租户ID（0 为平台公共字典） */
    @Schema(description = "租户ID")
    private Long tenantId;

    /** 字典名称 */
    @Schema(description = "字典名称")
    private String dictName;

    /** 字典类型（唯一键，如 sys_normal_disable） */
    @Schema(description = "字典类型")
    private String dictType;

    /** 状态：1 启用 / 0 禁用 */
    @Schema(description = "状态：1启用/0禁用")
    private Integer status;

    /** 备注 */
    @Schema(description = "备注")
    private String remark;

}
