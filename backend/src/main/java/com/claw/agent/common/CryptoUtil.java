package com.claw.agent.common;

import com.claw.agent.config.infra.ClawProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM 加解密工具：用于数据库中敏感配置（如模型 API Key）的加密存储。
 * <p>
 * 密文格式：enc:Base64(IV || ciphertext || tag)，读取时按前缀识别是否解密。
 * 密钥由 claw.jwt.secret 派生（SHA-256 取 32 字节），生产环境务必替换该密钥。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CryptoUtil {

    /** 密文前缀标识 */
    private static final String ENC_PREFIX = "enc:";
    /** 掩码中间段标识（mask 方法输出特征，用于识别"非真实密钥"的回传值） */
    public static final String MASK_INFIX = "****";
    /** GCM IV 长度（字节） */
    private static final int GCM_IV_LENGTH = 12;
    /** GCM 认证标签长度（位） */
    private static final int GCM_TAG_BITS = 128;
    /** AES 算法 */
    private static final String AES = "AES";
    /** AES-GCM 变换 */
    private static final String AES_GCM = "AES/GCM/NoPadding";

    private final ClawProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    /** AES-256 密钥（由配置密钥派生，懒加载） */
    private SecretKeySpec secretKey;

    /**
     * 加密明文；输入为空则原样返回。
     *
     * @param plainText 明文
     * @return enc: 前缀的 Base64 密文
     */
    public String encrypt(String plainText) {
        if (!StringUtils.hasText(plainText) || plainText.startsWith(ENC_PREFIX)) {
            return plainText;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, getKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            // IV 与密文拼接后整体 Base64
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return ENC_PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new BizException(ResultCode.AGENT_ERROR, "配置加密失败: " + e.getMessage());
        }
    }

    /**
     * 解密带 enc: 前缀的密文；无前缀视为明文原样返回（兼容历史数据）。
     *
     * @param value 密文或明文
     * @return 明文
     */
    public String decrypt(String value) {
        if (!StringUtils.hasText(value) || !value.startsWith(ENC_PREFIX)) {
            return value;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(value.substring(ENC_PREFIX.length()));
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, getKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] decrypted = cipher.doFinal(combined, GCM_IV_LENGTH, combined.length - GCM_IV_LENGTH);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("配置解密失败，请确认 claw.jwt.secret 与加密时一致", e);
            throw new BizException(ResultCode.AGENT_ERROR, "配置解密失败");
        }
    }

    /** 脱敏展示：仅保留前 2 后 2 位，避免接口明文回显 API Key */
    public String mask(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String plain = value.startsWith(ENC_PREFIX) ? "******" : value;
        if (plain.length() <= 6) {
            return "******";
        }
        return plain.substring(0, 2) + "****" + plain.substring(plain.length() - 2);
    }

    /**
     * 判断值是否为掩码展示值（如 ab****cd 或 ******）：
     * 编辑场景前端回传掩码值时应保留原密钥，不得将掩码写入库。
     *
     * @param value 待判断的值
     * @return true 表示是掩码值而非真实密钥/密文
     */
    public static boolean isMasked(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return value.contains(MASK_INFIX) || "******".equals(value);
    }

    /** 由配置密钥派生 AES-256 Key */
    private synchronized SecretKeySpec getKey() {
        if (secretKey == null) {
            try {
                byte[] raw = properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
                byte[] hash = MessageDigest.getInstance("SHA-256").digest(raw);
                secretKey = new SecretKeySpec(hash, AES);
            } catch (Exception e) {
                throw new BizException(ResultCode.AGENT_ERROR, "密钥派生失败: " + e.getMessage());
            }
        }
        return secretKey;
    }
}
