package com.claw.agent.config.tool;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OCR 识别统一配置（application.yml 的 claw.ocr 段）。
 * <p>
 * 支持多厂商后端，当前已接入百度智能云和腾讯云。
 * 运行时按优先级依次尝试：百度 → 腾讯，首个凭证可用的厂商处理请求。
 * <p>
 * 各厂商凭证优先从数据库三级作用域解析，未配置时回退到 application.yml 默认值。
 */
@Data
@Component
@ConfigurationProperties(prefix = "claw.ocr")
public class OcrConfig {

    /** 百度智能云 OCR 配置 */
    private BaiduOcr baidu = new BaiduOcr();

    /** 腾讯云 OCR 配置 */
    private TencentOcr tencent = new TencentOcr();

    /**
     * 百度智能云 OCR 凭证。
     * <p>
     * 控制台申请：https://console.bce.baidu.com/ai/#/ai/ocr/overview/index
     */
    @Data
    public static class BaiduOcr {
        /** API Key（数据库优先，此处仅为回退默认值） */
        private String apiKey;
        /** Secret Key */
        private String secretKey;
        /** Token 端点（OAuth 2.0 授权，一般无需修改） */
        private String tokenUrl = "https://aip.baidubce.com/oauth/2.0/token";
        /** 通用文字识别端点 */
        private String generalBasicUrl = "https://aip.baidubce.com/rest/2.0/ocr/v1/general_basic";
        /** 手写文字识别端点 */
        private String handwritingUrl = "https://aip.baidubce.com/rest/2.0/ocr/v1/handwriting";
    }

    /**
     * 腾讯云 OCR 凭证。
     * <p>
     * 控制台申请：https://console.cloud.tencent.com/cam/capi
     */
    @Data
    public static class TencentOcr {
        /** SecretId（数据库优先，此处仅为回退默认值） */
        private String secretId;
        /** SecretKey */
        private String secretKey;
        /** API 端点（一般无需修改） */
        private String endpoint = "ocr.tencentcloudapi.com";
        /** 地域（如 ap-guangzhou / ap-beijing / ap-shanghai） */
        private String region = "ap-guangzhou";
    }
}
