package com.claw.agent.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 模型提供商配置实体（表 model_provider_config，三级作用域）。
 * <p>
 * 作用域解析优先级：USER &gt; TENANT &gt; GLOBAL，就近覆盖；
 * 修改后通过配置服务失效缓存并热重建 Agent，无需重启应用。
 */
@Data
@EqualsAndHashCode(callSuper=false)
@TableName("model_provider_config")
public class ModelProviderConfig extends BaseEntity {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 作用域：GLOBAL / TENANT / USER */
    private String scope;

    /** 租户ID（GLOBAL 为 0） */
    private Long tenantId;

    /** 归属用户ID（USER 作用域为 sys_user.id，非 USER 为 null） */
    private String ownerId;

    /** 提供商标识：openai / dashscope / ollama */
    private String provider;

    /** 展示名称 */
    private String displayName;

    /** 是否启用：1 启用 / 0 禁用 */
    private Integer enabled;

    /** 是否该作用域内当前生效提供商 */
    private Integer isCurrent;

    /** API Key（AES 加密存储，前缀 enc:；ollama 无需） */
    private String apiKey;

    /** 自定义端点；openai 兼容协议可指向 DeepSeek / Kimi / vLLM */
    private String baseUrl;

    /** 模型标识，如 qwen-plus / gpt-4.1-mini */
    private String modelName;

    /** 扩展参数（JSON），如 {"temperature":0.7} */
    private String extraConfig;

    /** 备注 */
    private String remark;

    /** 是否当前生效提供商 */
    public boolean isCurrentProvider() {
        return Integer.valueOf(1).equals(isCurrent);
    }

    // 禁止手写 isEnabled()：与 Lombok 生成的 getEnabled() 在 MyBatis 反射中
    // 对属性 enabled 构成歧义（AmbiguousMethodInvoker），update/insert 参数绑定直接报错；
    // 启用判定请在服务层用 Integer.valueOf(1).equals(cfg.getEnabled())
}
