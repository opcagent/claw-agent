package com.claw.agent.config.infra;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * claw-agent 自定义配置属性（对应 application.yml 中 claw.* 段）。
 * <p>
 * 只保留启动期必须的基础配置（JWT / 上传 / 工作区路径）；
 * 模型提供商与 Agent 运行参数已落库（三级作用域），由 ConfigService 管理。
 * 阿里规约：配置集中收敛到属性类，禁止在业务代码中散落读取配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "claw")
public class ClawProperties {

    /** 平台版本信息（对外展示用，不含敏感项） */
    private Version version = new Version();

    /** JWT 相关配置 */
    private Jwt jwt = new Jwt();

    /** 文件上传配置 */
    private Upload upload = new Upload();

    /** Agent 基础配置（模型与运行参数已数据库化，见 ConfigService） */
    private Agent agent = new Agent();

    /** 跨域配置（前后端分离部署时放行前端源） */
    private Cors cors = new Cors();

    /** Redis 可选配置（未安装 Redis 时自动降级到本地 JSON 存储） */
    private Redis redis = new Redis();

    /** 平台版本信息：页脚版本号与通知中心发布说明的数据源 */
    @Data
    public static class Version {
        /** 版本号（如 1.0.0） */
        private String number = "1.0.0";
        /** 产品名 */
        private String name = "Claw Agent";
        /** 发布日期 */
        private String releaseDate;
        /** 本版亮点（通知中心展示） */
        private List<String> highlights = List.of();
    }

    /** JWT 配置 */
    @Data
    public static class Jwt {
        /** 签名密钥（生产环境务必通过环境变量覆盖） */
        private String secret;
        /** token 有效期（小时） */
        private int expirationHours = 24;
    }

    /** 文件上传配置 */
    @Data
    public static class Upload {
        /** 上传文件存储目录 */
        private String dir = "D:/claw-agent/data/uploads";
        /** 单文件大小上限（MB） */
        private int maxSizeMb = 20;
        /** 扩展名白名单（小写不带点；安全规约强制，禁止上传可执行/脚本类文件） */
        private List<String> allowedExtensions = List.of(
                "png", "jpg", "jpeg", "gif", "webp", "bmp",
                "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
                "txt", "md", "csv", "json");
    }

    /** Agent 基础配置 */
    @Data
    public static class Agent {
        /** Agent 名称 */
        private String name = "claw-assistant";
        /** AgentScope 工作区根路径（实际按 租户/用户 分子目录隔离） */
        private String workspace = "D:/claw-agent/.agentscope/workspace";
    }

    /** Redis 可选配置：未安装 Redis 时自动降级到本地 JSON 文件存储 */
    @Data
    public static class Redis {
        /**
         * Redis 开关：true 启用 / false 禁用 / auto 启动时自动探测。
         * 自动探测模式下会尝试连接 Redis，连接失败则自动降级到本地 JSON 存储。
         */
        private String enabled = "auto";
    }

    /** 跨域配置：前后端分离时前端独立域名/端口部署，需后端显式放行 */
    @Data
    public static class Cors {
        /** 允许的来源（支持 Ant 风格通配，生产环境收敛为具体域名） */
        private List<String> allowedOriginPatterns =
                List.of("http://localhost:3000", "http://127.0.0.1:3000");
        /** 预检请求缓存时长（秒） */
        private long maxAge = 3600;
    }
}
