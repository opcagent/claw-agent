package com.claw.agent.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 提示词模板实体（表 prompt_template，三级作用域）。
 * <p>
 * content 内含 {{变量}} 占位符：前端渲染表单让用户填参，
 * 服务端 render(vars) 替换后作为用户消息发送给 Agent。
 */
@Data
@EqualsAndHashCode(callSuper=false)
@TableName("prompt_template")
public class PromptTemplate extends BaseEntity {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 作用域：PLATFORM / TENANT / USER */
    private String scope;

    /** 租户ID（PLATFORM 为 0） */
    private Long tenantId;

    /** 归属用户ID（USER 作用域为 sys_user.id，非 USER 为 null） */
    private String ownerId;

    /** 模板编码（同作用域内唯一） */
    private String templateCode;

    /** 模板名称 */
    private String templateName;

    /** 分类（如 写作 / 汇报 / 分析） */
    private String category;

    /** 模板内容（含 {{变量}} 占位符） */
    private String content;

    /** 显示顺序 */
    private Integer orderNum;

    /** 是否启用：1 启用 / 0 禁用 */
    private Integer enabled;

    /**
     * 渲染模板：把 {{key}} 占位符替换为参数值；缺失的变量保留原占位符。
     *
     * @param vars 变量名到值的映射（可为 null）
     * @return 渲染后的文本
     */
    public String render(java.util.Map<String, String> vars) {
        if (content == null) {
            return "";
        }
        if (vars == null || vars.isEmpty()) {
            return content;
        }
        String result = content;
        for (java.util.Map.Entry<String, String> entry : vars.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }
        }
        return result;
    }
}
