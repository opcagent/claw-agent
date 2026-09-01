package com.claw.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.claw.agent.common.BizException;
import com.claw.agent.common.CryptoUtil;
import com.claw.agent.common.ResultCode;
import com.claw.agent.mapper.EmailConfigMapper;
import com.claw.agent.model.EmailConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Properties;

/**
 * 邮件发送服务。
 * <p>
 * 支持用户自定义 SMTP 配置,Agent 可调用此服务发送邮件通知。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final EmailConfigMapper emailConfigMapper;
    private final CryptoUtil cryptoUtil;

    /**
     * 获取用户的默认邮箱配置。
     *
     * @param userId   用户ID
     * @param tenantId 租户ID
     * @return 邮箱配置,如果不存在则返回 null
     */
    public EmailConfig getDefaultConfig(String userId, Long tenantId) {
        LambdaQueryWrapper<EmailConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmailConfig::getUserId, userId)
               .eq(EmailConfig::getTenantId, tenantId)
               .eq(EmailConfig::getEnabled, true)
               .orderByDesc(EmailConfig::getDefaultFlag)
               .last("LIMIT 1");

        return emailConfigMapper.selectOne(wrapper);
    }

    /**
     * 获取用户的所有邮箱配置列表。
     *
     * @param userId   用户ID
     * @param tenantId 租户ID
     * @return 邮箱配置列表
     */
    public List<EmailConfig> getUserConfigs(String userId, Long tenantId) {
        LambdaQueryWrapper<EmailConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmailConfig::getUserId, userId)
               .eq(EmailConfig::getTenantId, tenantId)
               .orderByDesc(EmailConfig::getCreateTime);

        return emailConfigMapper.selectList(wrapper);
    }

    /**
     * 保存或更新邮箱配置。
     *
     * @param config 邮箱配置 (密码需明文传入,内部会加密存储)
     */
    public void saveConfig(EmailConfig config) {
        // 密码加密存储
        if (config.getSmtpPassword() != null && !config.getSmtpPassword().isEmpty()) {
            config.setSmtpPassword(cryptoUtil.encrypt(config.getSmtpPassword()));
        }

        if (config.getId() == null) {
            // 新增
            emailConfigMapper.insert(config);
            log.info("已保存邮箱配置: user={}, email={}", config.getUserId(), config.getFromEmail());
        } else {
            // 更新
            emailConfigMapper.updateById(config);
            log.info("已更新邮箱配置: id={}, email={}", config.getId(), config.getFromEmail());
        }
    }

    /**
     * 删除邮箱配置。
     *
     * @param id 配置ID
     */
    public void deleteConfig(Long id) {
        emailConfigMapper.deleteById(id);
        log.info("已删除邮箱配置: id={}", id);
    }

    /**
     * 发送邮件。
     *
     * @param userId      用户ID
     * @param tenantId    租户ID
     * @param to          收件人邮箱
     * @param subject     邮件主题
     * @param content     邮件内容 (HTML 格式)
     * @param isHtml      是否为 HTML 格式
     * @param configId    邮箱配置ID (可选,不传则使用默认配置)
     */
    public void sendEmail(String userId, Long tenantId, String to, String subject, 
                         String content, boolean isHtml, Long configId) {
        // 1. 获取邮箱配置
        EmailConfig config;
        if (configId != null) {
            config = emailConfigMapper.selectById(configId);
            if (config == null) {
                throw new BizException(ResultCode.PARAM_ERROR, "邮箱配置不存在");
            }
        } else {
            config = getDefaultConfig(userId, tenantId);
            if (config == null) {
                throw new BizException(ResultCode.PARAM_ERROR, "未找到默认邮箱配置,请先配置 SMTP");
            }
        }

        // 2. 验证配置
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            throw new BizException(ResultCode.PARAM_ERROR, "邮箱配置已禁用");
        }

        // 3. 解密密码
        String password = cryptoUtil.decrypt(config.getSmtpPassword());

        // 4. 构建 JavaMailSender
        JavaMailSender mailSender = buildMailSender(config, password);

        // 5. 构建并发送邮件
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            String fromName = config.getFromName() != null ? config.getFromName() : config.getFromEmail();
            helper.setFrom(config.getFromEmail(), fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, isHtml);

            mailSender.send(message);

            log.info("邮件发送成功: from={}, to={}, subject={}", config.getFromEmail(), to, subject);

        } catch (MessagingException e) {
            log.error("邮件发送失败: to={}, subject={}", to, subject, e);
            throw new BizException(ResultCode.BIZ_ERROR, "邮件发送失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("邮件发送异常: to={}, subject={}", to, subject, e);
            throw new BizException(ResultCode.BIZ_ERROR, "邮件发送异常: " + e.getMessage());
        }
    }

    /**
     * 构建 JavaMailSender。
     *
     * @param config   邮箱配置
     * @param password 解密后的密码
     * @return JavaMailSender 实例
     */
    private JavaMailSender buildMailSender(EmailConfig config, String password) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(config.getSmtpHost());
        mailSender.setPort(config.getSmtpPort());
        mailSender.setUsername(config.getSmtpUsername());
        mailSender.setPassword(password);

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");

        if (Boolean.TRUE.equals(config.getSmtpUseSsl())) {
            props.put("mail.smtp.ssl.enable", "true");
        }

        if (Boolean.TRUE.equals(config.getSmtpUseTls())) {
            props.put("mail.smtp.starttls.enable", "true");
        }

        // 超时设置
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.connectiontimeout", "10000");

        return mailSender;
    }
}
