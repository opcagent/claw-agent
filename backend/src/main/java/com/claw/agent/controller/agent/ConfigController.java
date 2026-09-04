package com.claw.agent.controller.agent;

import com.claw.agent.common.*;
import com.claw.agent.config.infra.ClawProperties;
import com.claw.agent.model.AgentConfigItem;
import com.claw.agent.model.ModelProviderConfig;
import com.claw.agent.model.dto.ParamKeyInfo;
import com.claw.agent.security.LoginUser;
import com.claw.agent.service.ConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * Agent 配置管理控制器：模型提供商与运行参数的三级作用域配置。
 * <p>
 * 权限分级：GLOBAL 仅平台管理员可改；TENANT 需租户管理员及以上；
 * USER 任何登录用户可改（仅影响本人）。
 * 方法级鉴权：类级要求已登录；scope 为运行时入参（含请求体），无法用静态表达式
 * 完整判定，作用域级细粒度校验由 {@code checkScopePermission} 承担。
 * 保存成功后 ConfigService 发布变更事件，AgentRegistry 自动热重建受影响用户的 Agent。
 */
@Tag(name = "Agent 配置", description = "模型提供商/Agent 参数/工具配置")
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ConfigController {

    /** 操作日志模块名 */
    private static final String MODULE = "Agent配置";

    private final ConfigService configService;
    private final ClawProperties properties;

    /** 查询模型提供商列表（API Key 脱敏） */
    @Operation(summary = "模型提供商列表", description = "查询模型提供商配置列表（API Key 脱敏）")
    @GetMapping("/providers")
    public Mono<Result<List<ModelProviderConfig>>> listProviders(@RequestParam String scope) {
        return ReactiveSupport.call(user -> {
            checkScopePermission(user, scope);
            return configService.listProviders(scope,
                    resolveTenantId(user, scope), resolveOwnerId(user, scope));
        });
    }

    /** 保存模型提供商配置（新增或更新；apiKey 回传掩码/空时保留原密钥） */
    @Operation(summary = "保存模型提供商", description = "新增或更新模型提供商配置（apiKey 脱敏值时保留原密钥）")
    @PostMapping("/providers")
    public Mono<Result<Void>> saveProvider(@RequestBody ModelProviderConfig cfg) {
        return ReactiveSupport.run(MODULE, OperType.UPDATE, "保存模型配置", user -> {
            checkScopePermission(user, cfg.getScope());
            cfg.setTenantId(resolveTenantId(user, cfg.getScope()));
            cfg.setOwnerId(resolveOwnerId(user, cfg.getScope()));
            configService.saveProviderConfig(cfg);
        });
    }

    /** 查询运行参数列表 */
    @Operation(summary = "运行参数列表", description = "查询 Agent 运行参数配置列表")
    @GetMapping("/params")
    public Mono<Result<List<AgentConfigItem>>> listParams(@RequestParam String scope) {
        return ReactiveSupport.call(user -> {
            checkScopePermission(user, scope);
            return configService.listAgentConfigs(scope,
                    resolveTenantId(user, scope), resolveOwnerId(user, scope));
        });
    }

    /** 保存运行参数（键值对） */
    @Operation(summary = "保存运行参数", description = "保存 Agent 运行参数键值对")
    @PostMapping("/params")
    public Mono<Result<Void>> saveParam(@RequestBody ParamRequest request) {
        return ReactiveSupport.run(MODULE, OperType.UPDATE, "保存运行参数", user -> {
            checkScopePermission(user, request.getScope());
            configService.saveAgentConfig(request.getScope(),
                    resolveTenantId(user, request.getScope()),
                    resolveOwnerId(user, request.getScope()),
                    request.getConfigKey(), request.getConfigValue(), request.getRemark());
        });
    }

    /** 已知运行参数目录（键/说明/默认值/可选值），供管理页「快速添加」 */
    @Operation(summary = "参数目录", description = "已知运行参数目录（键/说明/默认值/可选值），供管理页快速添加")
    @GetMapping("/paramKeys")
    public Mono<Result<List<ParamKeyInfo>>> listParamKeys() {
        return ReactiveSupport.call(user -> configService.listParamKeys());
    }

    /**
     * 平台级系统配置只读视图（读取自系统配置，非数据库配置）。
     * <p>
     * 这类配置涉及启动期资源初始化（安全过滤/静态目录），不提供页面修改，
     * 但在管理页可见，避免「配置存在但无处可查」；
     * 敏感项（JWT 密钥）与部署内部路径（存储目录/工作区）不下发，避免暴露项目结构。
     */
    @Operation(summary = "系统属性", description = "系统运行时属性（JVM/OS/内存等），仅管理员可查看")
    @GetMapping("/systemProps")
    public Mono<Result<SystemProps>> systemProps() {
        return ReactiveSupport.call(user -> SystemProps.from(properties));
    }

    /**
     * 平台版本信息（版本号 / 产品名 / 发布日期 / 本版亮点）。
     * <p>
     * 供前端页脚版本号与右上角通知中心展示；发布说明随部署配置维护，
     * 升级时更新配置即可触达用户，无需新增数据库表。
     */
    @Operation(summary = "版本信息", description = "获取平台版本信息（放行接口，无需登录）")
    @GetMapping({"/versionInfo", "/version-info"})
    @PreAuthorize("permitAll()")
    public Mono<Result<VersionInfo>> versionInfo() {
        // 登录页也需要展示品牌名，不能依赖登录用户，直接返回配置
        return Mono.fromCallable(() -> Result.ok(VersionInfo.from(properties)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    // ------------------------------------------------------------
    // 搜索引擎 API Key 配置（三级作用域，加密存储）
    // ------------------------------------------------------------

    /** 查询某作用域下的搜索引擎配置（API Key 脱敏回显） */
    @Operation(summary = "搜索引擎配置", description = "查询某作用域下的搜索引擎配置（API Key 脱敏）")
    @GetMapping("/searchConfigs")
    public Mono<Result<List<AgentConfigItem>>> listSearchConfigs(@RequestParam String scope) {
        return ReactiveSupport.call(user -> {
            checkScopePermission(user, scope);
            return configService.listSearchConfigs(scope,
                    resolveTenantId(user, scope), resolveOwnerId(user, scope));
        });
    }

    /** 保存搜索引擎配置（API Key 加密存储；空值或脱敏值保留原密钥） */
    @Operation(summary = "保存搜索引擎配置", description = "保存搜索引擎配置（API Key 加密存储）")
    @PostMapping("/searchConfigs")
    public Mono<Result<Void>> saveSearchConfig(@RequestBody SearchConfigRequest request) {
        return ReactiveSupport.run(MODULE, OperType.UPDATE, "保存搜索配置", user -> {
            checkScopePermission(user, request.getScope());
            // 空值或脱敏值（****）时跳过，保留原密钥
            if (!StringUtils.hasText(request.getConfigValue())
                    || "****".equals(request.getConfigValue())) {
                return;
            }
            configService.saveSearchConfig(request.getScope(),
                    resolveTenantId(user, request.getScope()),
                    resolveOwnerId(user, request.getScope()),
                    request.getConfigKey(), request.getConfigValue());
        });
    }

    // ------------------------------------------------------------
    // 作用域权限与归属解析
    // ------------------------------------------------------------

    /** 校验当前用户是否有权操作目标作用域 */
    private void checkScopePermission(LoginUser user, String scope) {
        if (ConfigService.SCOPE_PLATFORM.equals(scope)) {
            if (!user.isAdmin()) {
                throw new BizException(ResultCode.FORBIDDEN, "仅平台管理员可维护全局配置");
            }
        } else if (ConfigService.SCOPE_TENANT.equals(scope)) {
            if (!user.isTenantAdmin()) {
                throw new BizException(ResultCode.FORBIDDEN, "仅租户管理员可维护租户配置");
            }
        }
        // USER 作用域：任何登录用户可维护本人配置
    }

    /** 解析目标作用域的租户ID（GLOBAL 固定 0；TENANT/USER 取本人租户） */
    private Long resolveTenantId(LoginUser user, String scope) {
        return ConfigService.SCOPE_PLATFORM.equals(scope) ? 0L : user.getTenantId();
    }

    /** 解析归属用户ID（仅 USER 作用域取本人） */
    private String resolveOwnerId(LoginUser user, String scope) {
        return ConfigService.SCOPE_USER.equals(scope) ? user.getUserId() : null;
    }

    /** 运行参数保存请求 */
    @Data
    public static class ParamRequest {
        /** 作用域：GLOBAL / TENANT / USER */
        private String scope;
        /** 配置键 */
        private String configKey;
        /** 配置值 */
        private String configValue;
        /** 说明 */
        private String remark;
    }

    /** 搜索引擎配置保存请求 */
    @Data
    public static class SearchConfigRequest {
        /** 作用域：PLATFORM / TENANT / USER */
        private String scope;
        /** 配置键（search.tavily.api_key 等） */
        private String configKey;
        /** 配置值（API Key 明文，后端加密存储） */
        private String configValue;
    }

    /** 平台级系统配置只读视图（脱敏后下发：排除 JWT 密钥与部署内部路径） */
    @Data
    public static class SystemProps {
        /** 单文件大小上限（MB） */
        private int uploadMaxSizeMb;
        /** 上传扩展名白名单 */
        private List<String> uploadAllowedExtensions;
        /** token 有效期（小时） */
        private int jwtExpirationHours;
        /** 跨域放行的来源 */
        private List<String> corsAllowedOrigins;
        /** Agent 名称 */
        private String agentName;

        /** 从配置属性构建视图（不拷贝敏感字段与部署内部路径） */
        public static SystemProps from(ClawProperties p) {
            SystemProps v = new SystemProps();
            v.uploadMaxSizeMb = p.getUpload().getMaxSizeMb();
            v.uploadAllowedExtensions = p.getUpload().getAllowedExtensions();
            v.jwtExpirationHours = p.getJwt().getExpirationHours();
            v.corsAllowedOrigins = p.getCors().getAllowedOriginPatterns();
            v.agentName = p.getAgent().getName();
            return v;
        }
    }

    /** 平台版本信息视图（通知中心与页脚展示用，无敏感项） */
    @Data
    public static class VersionInfo {
        /** 版本号 */
        private String version;
        /** 产品名 */
        private String name;
        /** 发布日期 */
        private String releaseDate;
        /** 本版亮点 */
        private List<String> highlights;

        /** 从配置属性构建视图 */
        public static VersionInfo from(ClawProperties p) {
            VersionInfo v = new VersionInfo();
            v.version = p.getVersion().getNumber();
            v.name = p.getVersion().getName();
            v.releaseDate = p.getVersion().getReleaseDate();
            v.highlights = p.getVersion().getHighlights();
            return v;
        }
    }
}
