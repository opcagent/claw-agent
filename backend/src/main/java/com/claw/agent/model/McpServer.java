package com.claw.agent.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MCP 服务器登记实体（表 mcp_server，三级作用域）。
 * <p>
 * Agent 构建时由 {@code McpServerRegistrar} 挂载各服务器暴露的工具；
 * 解析优先级与其他配置一致：USER &gt; TENANT &gt; GLOBAL，按服务器名就近覆盖。
 * headers / env 可含密钥，一律 AES 加密存储（前缀 enc:），编辑回显掩码。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mcp_server")
public class McpServer extends BaseEntity {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 作用域：GLOBAL / TENANT / USER */
    private String scope;

    /** 租户ID（GLOBAL 为 0） */
    private Long tenantId;

    /** 归属用户ID（USER 作用域为 sys_user.id，非 USER 为 null） */
    private String ownerId;

    /** MCP 服务器唯一名（同作用域内唯一，作为工具命名空间） */
    private String name;

    /** 传输方式：stdio / sse / http / streamable-http */
    private String transport;

    /** stdio 启动命令（transport=stdio 时必填） */
    private String command;

    /** stdio 启动参数 JSON 数组 */
    private String args;

    /** 服务端点（sse / http / streamable-http 时必填） */
    private String url;

    /** HTTP 请求头 JSON（AES 加密存储） */
    private String headers;

    /** stdio 环境变量 JSON（AES 加密存储） */
    private String env;

    /** 仅启用的工具名 JSON 数组（留空=全部启用） */
    private String enableTools;

    /** 是否启用：1 启用 / 0 禁用 */
    private Integer enabled;

    /** 备注 */
    private String remark;
}
