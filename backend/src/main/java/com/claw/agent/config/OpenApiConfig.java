package com.claw.agent.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3.0 文档配置（springdoc + Knife4j 增强 UI）。
 * <p>
 * 仅在开发阶段启用（通过 application.yml 的 springdoc.api-docs.enabled 控制）。
 * 生产环境务必关闭，避免接口信息泄露。
 * <p>
 * 访问地址：
 * - Swagger UI：http://localhost:8080/swagger-ui/index.html
 * - Knife4j：http://localhost:8080/doc.html
 * - OpenAPI JSON：http://localhost:8080/v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    /**
     * 自定义 OpenAPI 元信息（标题、描述、版本、联系方式等）。
     * springdoc 自动扫描 Controller 生成接口文档，此 Bean 仅补充全局元数据和认证方案。
     */
    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "BearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("Claw Agent 平台 API")
                        .version("1.0.0")
                        .description("基于 AgentScope Java 2.0 的个人 Agent 平台，"
                                + "支持多模型 / RBAC / 多租户 / 工具编排 / 渠道接入。")
                        .contact(new Contact()
                                .name("Claw Agent Team")))
                // 全局 JWT 认证方案（接口文档可在线调试带 Token 请求）
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("登录后获取 JWT Token，填入此处即可调试需认证接口")));
    }
}
