package com.claw.agent.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Agent 运行参数实体（表 agent_config，KV 结构 + 三级作用域）。
 * <p>
 * 作用域解析优先级：USER &gt; TENANT &gt; GLOBAL。
 * 常用键：state_store_type / permission_mode /
 * compaction_trigger_messages / compaction_keep_messages / memory_flush_throttle_minutes。
 */
@Data
@EqualsAndHashCode(callSuper=false)
@TableName("agent_config")
public class AgentConfigItem extends BaseEntity {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 作用域：GLOBAL / TENANT / USER */
    private String scope;

    /** 租户ID（GLOBAL 为 0） */
    private Long tenantId;

    /** 归属用户ID（USER 作用域为 sys_user.id，非 USER 为 null） */
    private String ownerId;

    /** 配置键（同作用域内唯一） */
    private String configKey;

    /** 配置值 */
    private String configValue;

    /** 配置说明 */
    private String remark;
}
