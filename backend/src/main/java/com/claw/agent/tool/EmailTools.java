package com.claw.agent.tool;

import com.claw.agent.common.ToolCodes;
import com.claw.agent.model.EmailConfig;
import com.claw.agent.security.SecurityUtil;
import com.claw.agent.service.EmailService;
import com.claw.agent.tool.annotation.ToolSet;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 邮件发送工具集。
 * <p>
 * Agent 可通过此工具发送邮件通知,邮箱配置跟随用户存储。
 */
@Slf4j
@RequiredArgsConstructor
@ToolSet(
    code = ToolCodes.EMAIL_TOOLS,
    name = "邮件工具",
    description = "提供 SMTP 邮件发送、配置管理等功能",
    category = "utility",
    enabledByDefault = true,
    version = "1.0.0"
)
public class EmailTools {

    private final EmailService emailService;

    /**
     * 发送邮件。
     * <p>
     * Agent 调用示例：
     * <pre>{@code
     * sendEmail(
     *   to: "user@example.com",
     *   subject: "测试邮件",
     *   content: "<h1>这是一封测试邮件</h1>",
     *   isHtml: true
     * )
     * }</pre>
     *
     * @param to      收件人邮箱地址
     * @param subject 邮件主题
     * @param content 邮件内容 (支持 HTML)
     * @param isHtml  是否为 HTML 格式 (默认 true)
     * @return 发送结果
     */
    @Tool(name = "sendEmail", description = "发送邮件通知。需要提供收件人、主题和内容，会自动使用当前用户的默认邮箱配置。")
    public String sendEmail(
            @ToolParam(name = "to", description = "收件人邮箱地址,如 user@example.com") String to,
            @ToolParam(name = "subject", description = "邮件主题") String subject,
            @ToolParam(name = "content", description = "邮件内容,支持 HTML 格式") String content,
            @ToolParam(name = "isHtml", description = "是否为 HTML 格式,默认 true", required = false) Boolean isHtml) {
        try {
            // 从 SecurityContext 获取当前用户信息
            String userId = SecurityUtil.getUserId();
            Long tenantId = SecurityUtil.getTenantId();
            
            if (userId == null || tenantId == null) {
                return "❌ 错误: 无法获取用户信息\n\n" +
                       "可能原因:\n" +
                       "1. 不在 HTTP 请求上下文中\n" +
                       "2. 未登录或 Token 过期\n\n" +
                       "解决方案:\n" +
                       "- 通过前端页面 /system/email-config 配置邮箱\n" +
                       "- 使用 HTTP API: POST /api/emailConfig/send";
            }

            // 验证收件人邮箱格式
            if (!isValidEmail(to)) {
                return "❌ 错误: 收件人邮箱格式不正确: " + to;
            }

            // 查询用户的默认邮箱配置
            EmailConfig config = emailService.getDefaultConfig(userId, tenantId);
            if (config == null) {
                return "❌ 错误: 未找到默认邮箱配置\n\n请先通过以下方式配置:\n" +
                       "1. 访问 /system/email-config 页面\n" +
                       "2. 新增 SMTP 配置\n" +
                       "3. 设为默认配置";
            }

            // 验证配置状态
            if (!Boolean.TRUE.equals(config.getEnabled())) {
                return "❌ 错误: 邮箱配置已禁用 (" + config.getFromEmail() + ")";
            }

            // 发送邮件
            emailService.sendEmail(
                    userId,
                    tenantId,
                    to,
                    subject,
                    content,
                    isHtml != null ? isHtml : true,
                    null  // 使用默认配置
            );

            return "✅ 邮件发送成功\n" +
                   "- 发件人: " + (config.getFromName() != null ? config.getFromName() : config.getFromEmail()) +
                   " <" + config.getFromEmail() + ">\n" +
                   "- 收件人: " + to + "\n" +
                   "- 主题: " + subject + "\n" +
                   "- SMTP: " + config.getSmtpHost() + ":" + config.getSmtpPort();

        } catch (Exception e) {
            log.error("Agent 调用邮件工具失败: to={}, subject={}", to, subject, e);
            return "❌ 邮件发送失败: " + e.getMessage();
        }
    }

    /**
     * 查询用户的邮箱配置列表。
     *
     * @return 配置列表或提示信息
     */
    @Tool(name = "listEmailConfigs", description = "查看已配置的邮箱账号列表")
    public String listEmailConfigs() {
        try {
            // 注意: Agent 调用时需要从 RuntimeContext 获取 userId 和 tenantId
            // 当前简化实现,返回提示信息
            return "⚠️ 邮箱配置查询需要通过 API 调用\n" +
                   "请使用 GET /api/emailConfig/list 接口";

        } catch (Exception e) {
            log.error("查询邮箱配置失败", e);
            return "❌ 查询失败: " + e.getMessage();
        }
    }

    /**
     * 验证邮箱格式。
     *
     * @param email 邮箱地址
     * @return 是否有效
     */
    private boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }
        // 简单的邮箱格式验证
        return email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    }
}
