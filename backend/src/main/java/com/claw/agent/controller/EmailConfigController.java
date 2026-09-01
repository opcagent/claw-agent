package com.claw.agent.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.claw.agent.common.BizException;
import com.claw.agent.common.ReactiveSupport;
import com.claw.agent.common.Result;
import com.claw.agent.common.ResultCode;
import com.claw.agent.mapper.UserMapper;
import com.claw.agent.model.EmailConfig;
import com.claw.agent.model.User;
import com.claw.agent.security.LoginUser;
import com.claw.agent.service.EmailService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 邮箱配置控制器。
 * <p>
 * 提供用户邮箱 SMTP 配置的增删改查,邮箱配置跟随用户存储。
 */
@Tag(name = "邮件配置", description = "邮件发送配置管理")
@RestController
@RequestMapping("/api/emailConfig")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class EmailConfigController {

    private final EmailService emailService;
    private final UserMapper userMapper;

    /**
     * 获取当前用户的邮箱配置列表。
     */
    @Operation(summary = "邮箱配置列表", description = "查询当前用户的所有邮箱配置")
    @GetMapping("/list")
    public Mono<Result<List<EmailConfig>>> listConfigs() {
        return ReactiveSupport.call(user -> {
            List<EmailConfig> configs = emailService.getUserConfigs(user.getUserId(), user.getTenantId());
            // 密码字段脱敏处理
            configs.forEach(config -> config.setSmtpPassword("***"));
            return configs;
        });
    }

    /**
     * 获取默认邮箱配置。
     */
    @Operation(summary = "默认邮箱配置", description = "获取当前用户的默认邮箱配置")
    @GetMapping("/default")
    public Mono<Result<EmailConfig>> getDefaultConfig() {
        return ReactiveSupport.call(user -> {
            EmailConfig config = emailService.getDefaultConfig(user.getUserId(), user.getTenantId());
            if (config != null) {
                config.setSmtpPassword("***");
            }
            return config;
        });
    }

    /**
     * 保存或更新邮箱配置。
     */
    @Operation(summary = "保存邮箱配置", description = "新增或更新邮箱 SMTP 配置")
    @PostMapping("/save")
    public Mono<Result<Void>> saveConfig(@RequestBody EmailConfig config) {
        return ReactiveSupport.run(user -> {
            // 设置用户信息
            config.setUserId(user.getUserId());
            config.setTenantId(user.getTenantId());
            // 如果密码是脱敏后的值,则不更新密码
            if ("***".equals(config.getSmtpPassword())) {
                config.setSmtpPassword(null);
            }
            emailService.saveConfig(config);
        });
    }

    /**
     * 删除邮箱配置。
     */
    @Operation(summary = "删除邮箱配置", description = "删除指定 ID 的邮箱配置")
    @DeleteMapping("/delete/{id}")
    public Mono<Result<Void>> deleteConfig(@PathVariable Long id) {
        return ReactiveSupport.run(user -> emailService.deleteConfig(id));
    }

    /**
     * 发送邮件。
     */
    @Operation(summary = "发送邮件", description = "通过指定邮箱发送邮件")
    @PostMapping("/send")
    public Mono<Result<String>> sendEmail(
            @RequestParam String to,
            @RequestParam String subject,
            @RequestParam String content,
            @RequestParam(required = false, defaultValue = "true") Boolean isHtml,
            @RequestParam(required = false) Long configId) {
        return ReactiveSupport.call(user -> {
            emailService.sendEmail(
                    user.getUserId(),
                    user.getTenantId(),
                    to,
                    subject,
                    content,
                    isHtml,
                    configId
            );
            return "邮件发送成功";
        });
    }

    /**
     * 测试邮箱配置是否可用。
     * <p>
     * 如果传入 to 参数,则发送到指定邮箱;否则发送到用户自己的邮箱。
     *
     * @param config 邮箱配置信息 (必须包含 from_email/smtp_host/smtp_port/smtp_username/smtp_password)
     * @param to     可选的收件人邮箱 (不传则使用用户的 email)
     * @return 测试结果
     */
    @Operation(summary = "测试邮箱连接", description = "测试指定邮箱配置的 SMTP 连接")
    @PostMapping("/test")
    public Mono<Result<String>> testConfig(
            @RequestBody EmailConfig config,
            @RequestParam(required = false) String to) {
        return ReactiveSupport.call(user -> {
            // 设置用户信息
            config.setUserId(user.getUserId());
            config.setTenantId(user.getTenantId());
            
            // 确定收件人
            String recipient;
            if (to != null && !to.isEmpty()) {
                // 使用指定的收件人
                recipient = to;
            } else {
                // 查询用户的邮箱地址
                User dbUser = userMapper.selectOne(
                    new LambdaQueryWrapper<User>()
                        .eq(User::getId, user.getUserId())
                        .select(User::getEmail)
                );
                
                if (dbUser == null || dbUser.getEmail() == null || dbUser.getEmail().isEmpty()) {
                    throw new BizException(ResultCode.BIZ_ERROR, "用户未配置邮箱地址，请先在个人资料中设置，或传入 to 参数指定收件人");
                }
                recipient = dbUser.getEmail();
            }
            
            // 验证收件人邮箱格式
            if (!recipient.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")) {
                throw new BizException(ResultCode.BIZ_ERROR, "收件人邮箱格式不正确: " + recipient);
            }
            
            // 发送测试邮件
            try {
                emailService.sendEmail(
                    user.getUserId(),
                    user.getTenantId(),
                    recipient,
                    "邮箱配置测试",
                    "<h1>测试邮件</h1><p>如果您收到这封邮件，说明 SMTP 配置正确！</p><p>发送时间: " + java.time.LocalDateTime.now() + "</p>",
                    true,
                    null
                );
                return "测试邮件已发送到: " + recipient;
            } catch (Exception e) {
                throw new BizException(ResultCode.BIZ_ERROR, "发送失败: " + e.getMessage());
            }
        });
    }
}
