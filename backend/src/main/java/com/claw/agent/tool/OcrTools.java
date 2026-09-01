package com.claw.agent.tool;

import com.claw.agent.common.ToolCodes;
import com.claw.agent.config.tool.OcrConfig;
import com.claw.agent.service.ConfigService;
import com.claw.agent.tool.annotation.ToolSet;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * OCR 文字识别工具集（多厂商降级链：百度智能云 → 腾讯云）。
 * <p>
 * 提供通用文字识别与手写文字识别能力。
 * 当底层模型不支持 Vision（图片理解）时，Agent 可主动调用此工具识别图片中的文字。
 * <p>
 * 运行时按优先级依次尝试：百度智能云 → 腾讯云，首个凭证可用的厂商处理请求。
 * 后续可按需扩展更多厂商（阿里云等），只需在 {@link #doOcr} 降级链中追加。
 * <p>
 * 各厂商凭证支持三级作用域数据库配置，未配置时回退到 application.yml 默认值。
 */
@Slf4j
@Component
@ToolSet(
    code = ToolCodes.OCR,
    name = "OCR识别",
    description = "OCR 图片文字识别（多厂商降级：百度智能云 → 腾讯云），支持通用印刷体和手写体",
    category = "ai",
    enabledByDefault = true,
    version = "2.0.0"
)
public class OcrTools {

    // ---- 腾讯云 TC3 签名常量 ----
    private static final String TC_SERVICE = "ocr";
    private static final String TC_ALGORITHM = "TC3-HMAC-SHA256";
    private static final String TC_CT_JSON = "application/json; charset=utf-8";
    private static final DateTimeFormatter TC_DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private final OcrConfig ocrConfig;
    private final ConfigService configService;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /** 百度 Access Token 缓存（有效期 30 天，提前 5 分钟刷新） */
    private volatile String cachedBaiduToken;
    private volatile long baiduTokenExpireTime;

    public OcrTools(OcrConfig ocrConfig, ConfigService configService) {
        this.ocrConfig = ocrConfig;
        this.configService = configService;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * 通用文字识别：识别图片中的印刷体文字。
     *
     * @param imagePath 图片文件的绝对路径
     * @param language  语言类型（百度: auto/CHN_ENG/ENG；腾讯: 自动识别，预留参数）
     * @param rc        运行时上下文（框架自动注入，含 userId / tenantId）
     * @return 识别结果文本（每行一条，含置信度）
     */
    @Tool(name = "ocr_recognize_text", description = "识别图片中的印刷体文字，返回识别文本和置信度")
    public String recognizeText(
            @ToolParam(name = "image_path", description = "图片文件的绝对路径") String imagePath,
            @ToolParam(name = "language", description = "语言类型: auto/CHN_ENG/ENG，默认auto", required = false) String language,
            RuntimeContext rc) {
        return doOcr(imagePath, language, "GeneralBasicOCR", rc);
    }

    /**
     * 手写文字识别：识别图片中的手写文字。
     *
     * @param imagePath 图片文件的绝对路径
     * @param language  语言类型（预留参数）
     * @param rc        运行时上下文
     * @return 识别结果文本
     */
    @Tool(name = "ocr_recognize_handwriting", description = "识别图片中的手写文字，返回识别文本和置信度")
    public String recognizeHandwriting(
            @ToolParam(name = "image_path", description = "图片文件的绝对路径") String imagePath,
            @ToolParam(name = "language", description = "语言类型（预留，当前自动识别）", required = false) String language,
            RuntimeContext rc) {
        return doOcr(imagePath, language, "HandwritingOCR", rc);
    }

    // ================================================================
    // 降级调度：百度 → 腾讯
    // ================================================================

    /**
     * 多厂商降级调度：优先百度智能云，百度未配置或调用失败时回退腾讯云。
     */
    private String doOcr(String imagePath, String language,
                         String tencentAction, RuntimeContext rc) {
        // 读取图片（公共前置步骤，两个厂商都需要）
        Path path = Paths.get(imagePath).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            return "错误：文件不存在 - " + imagePath;
        }

        // ① 尝试百度
        if (hasBaiduCredentials(rc)) {
            try {
                String result = callBaidu(path, language, tencentAction, rc);
                if (result != null) return result;
            } catch (Exception e) {
                log.warn("百度 OCR 调用失败，降级到腾讯云", e);
            }
        }

        // ② 尝试腾讯
        if (hasTencentCredentials(rc)) {
            try {
                return callTencent(path, tencentAction, rc);
            } catch (Exception e) {
                log.error("腾讯云 OCR 调用失败", e);
                return "OCR 识别失败: " + e.getMessage();
            }
        }

        return "错误：无可用 OCR 服务，请配置百度智能云或腾讯云的 OCR 凭证";
    }

    // ================================================================
    // 百度智能云实现
    // ================================================================

    private boolean hasBaiduCredentials(RuntimeContext rc) {
        String apiKey = resolveConfig(ConfigService.KEY_BAIDU_OCR_API_KEY,
                ocrConfig.getBaidu().getApiKey(), rc);
        String secretKey = resolveConfig(ConfigService.KEY_BAIDU_OCR_SECRET_KEY,
                ocrConfig.getBaidu().getSecretKey(), rc);
        return apiKey != null && !apiKey.isBlank()
                && secretKey != null && !secretKey.isBlank();
    }

    /**
     * 百度 OCR 调用：获取 Token → POST 识别 → 格式化。
     * 返回 null 表示调用异常需降级。
     */
    private String callBaidu(Path imagePath, String language,
                             String tencentAction, RuntimeContext rc) throws Exception {
        byte[] imageBytes = Files.readAllBytes(imagePath);
        String imageBase64 = Base64.getEncoder().encodeToString(imageBytes);

        // 映射腾讯 Action 名到百度端点
        String ocrUrl = "HandwritingOCR".equals(tencentAction)
                ? ocrConfig.getBaidu().getHandwritingUrl()
                : ocrConfig.getBaidu().getGeneralBasicUrl();

        String accessToken = getBaiduAccessToken(rc);
        if (accessToken == null) return null;

        String body = "image=" + java.net.URLEncoder.encode(imageBase64, "UTF-8")
                + "&language=" + (language != null && !language.isBlank() ? language : "auto")
                + "&detect_direction=true&paragraph=true";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ocrUrl + "?access_token=" + accessToken))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());
        JsonNode json = objectMapper.readTree(response.body());

        if (json.has("error_code")) {
            String errorCode = json.get("error_code").asText();
            String errorMsg = json.has("error_msg") ? json.get("error_msg").asText() : "未知错误";
            // Token 过期时清除缓存
            if ("110".equals(errorCode) || "111".equals(errorCode)) {
                cachedBaiduToken = null;
                baiduTokenExpireTime = 0;
            }
            log.warn("百度 OCR 返回错误 [{}]: {}", errorCode, errorMsg);
            return null; // 降级到腾讯
        }

        return formatBaiduResult(json);
    }

    private String getBaiduAccessToken(RuntimeContext rc) {
        if (cachedBaiduToken != null && System.currentTimeMillis() < baiduTokenExpireTime - 300_000) {
            return cachedBaiduToken;
        }
        try {
            String apiKey = resolveConfig(ConfigService.KEY_BAIDU_OCR_API_KEY,
                    ocrConfig.getBaidu().getApiKey(), rc);
            String secretKey = resolveConfig(ConfigService.KEY_BAIDU_OCR_SECRET_KEY,
                    ocrConfig.getBaidu().getSecretKey(), rc);
            if (apiKey == null || secretKey == null) return null;

            String url = ocrConfig.getBaidu().getTokenUrl()
                    + "?grant_type=client_credentials"
                    + "&client_id=" + java.net.URLEncoder.encode(apiKey, "UTF-8")
                    + "&client_secret=" + java.net.URLEncoder.encode(secretKey, "UTF-8");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(response.body());

            if (json.has("access_token")) {
                cachedBaiduToken = json.get("access_token").asText();
                long expiresIn = json.has("expires_in") ? json.get("expires_in").asLong(2592000) : 2592000;
                baiduTokenExpireTime = System.currentTimeMillis() + expiresIn * 1000;
                return cachedBaiduToken;
            }
            log.warn("获取百度 OCR Token 失败: {}", response.body());
            return null;
        } catch (Exception e) {
            log.warn("获取百度 OCR Token 异常", e);
            return null;
        }
    }

    private String formatBaiduResult(JsonNode json) {
        JsonNode wordsResult = json.get("words_result");
        if (wordsResult == null || !wordsResult.isArray() || wordsResult.isEmpty()) {
            return "未识别到文字内容";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("识别结果（共 ").append(wordsResult.size()).append(" 行）：\n\n");
        int paragraphId = -1;
        for (JsonNode item : wordsResult) {
            if (item.has("paragraph")) {
                int cur = item.get("paragraph").asInt(-1);
                if (cur != paragraphId && cur >= 0 && paragraphId >= 0) sb.append("\n");
                paragraphId = cur;
            }
            sb.append(item.get("words").asText());
            if (item.has("probability")) {
                double conf = item.get("probability").get("average").asDouble();
                if (conf < 0.9) sb.append(String.format(" [置信度:%.0f%%]", conf * 100));
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    // ================================================================
    // 腾讯云实现（TC3-HMAC-SHA256 签名）
    // ================================================================

    private boolean hasTencentCredentials(RuntimeContext rc) {
        String secretId = resolveConfig(ConfigService.KEY_TENCENT_OCR_SECRET_ID,
                ocrConfig.getTencent().getSecretId(), rc);
        String secretKey = resolveConfig(ConfigService.KEY_TENCENT_OCR_SECRET_KEY,
                ocrConfig.getTencent().getSecretKey(), rc);
        return secretId != null && !secretId.isBlank()
                && secretKey != null && !secretKey.isBlank();
    }

    /**
     * 腾讯云 OCR 调用：TC3 签名 → POST → 格式化。
     */
    private String callTencent(Path imagePath, String action,
                               RuntimeContext rc) throws Exception {
        byte[] imageBytes = Files.readAllBytes(imagePath);
        String imageBase64 = Base64.getEncoder().encodeToString(imageBytes);

        String secretId = resolveConfig(ConfigService.KEY_TENCENT_OCR_SECRET_ID,
                ocrConfig.getTencent().getSecretId(), rc);
        String secretKey = resolveConfig(ConfigService.KEY_TENCENT_OCR_SECRET_KEY,
                ocrConfig.getTencent().getSecretKey(), rc);

        String payload = objectMapper.writeValueAsString(
                java.util.Map.of("ImageBase64", imageBase64));

        Instant now = Instant.now();
        String responseBody = tencentSignAndSend(secretId, secretKey, now, action, payload);

        JsonNode json = objectMapper.readTree(responseBody);
        JsonNode response = json.has("Response") ? json.get("Response") : json;

        if (response.has("Error")) {
            JsonNode error = response.get("Error");
            String code = error.has("Code") ? error.get("Code").asText() : "Unknown";
            String msg = error.has("Message") ? error.get("Message").asText() : "未知错误";
            throw new RuntimeException("腾讯云 OCR 错误 [" + code + "]: " + msg);
        }

        return formatTencentResult(response);
    }

    private String tencentSignAndSend(String secretId, String secretKey,
                                      Instant timestamp, String action,
                                      String payload) throws Exception {
        String endpoint = ocrConfig.getTencent().getEndpoint();
        String region = ocrConfig.getTencent().getRegion();
        String date = TC_DATE_FMT.format(timestamp);
        long ts = timestamp.getEpochSecond();

        String hashedPayload = sha256Hex(payload);
        String canonicalHeaders = "content-type:" + TC_CT_JSON + "\n"
                + "host:" + endpoint + "\n"
                + "x-tc-action:" + action.toLowerCase() + "\n";
        String signedHeaders = "content-type;host;x-tc-action";
        String canonicalRequest = "POST\n/\n\n" + canonicalHeaders + "\n"
                + signedHeaders + "\n" + hashedPayload;

        String credentialScope = date + "/" + TC_SERVICE + "/tc3_request";
        String stringToSign = TC_ALGORITHM + "\n" + ts + "\n"
                + credentialScope + "\n" + sha256Hex(canonicalRequest);

        byte[] secretDate = hmac256(("TC3" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        byte[] secretService = hmac256(secretDate, TC_SERVICE);
        byte[] secretSigning = hmac256(secretService, "tc3_request");
        String signature = bytesToHex(hmac256(secretSigning, stringToSign));

        String authorization = TC_ALGORITHM
                + " Credential=" + secretId + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + signature;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://" + endpoint))
                .header("Authorization", authorization)
                .header("Content-Type", TC_CT_JSON)
                // Host 为 HttpClient 受限头，由 URI 自动设置，手动设置会抛 IllegalArgumentException
                .header("X-TC-Action", action)
                .header("X-TC-Version", "2018-11-19")
                .header("X-TC-Timestamp", String.valueOf(ts))
                .header("X-TC-Region", region)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .timeout(Duration.ofSeconds(30))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    private String formatTencentResult(JsonNode response) {
        JsonNode detections = response.get("TextDetections");
        if (detections == null || !detections.isArray() || detections.isEmpty()) {
            return "未识别到文字内容";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("识别结果（共 ").append(detections.size()).append(" 行）：\n\n");
        for (JsonNode item : detections) {
            sb.append(item.has("DetectedText") ? item.get("DetectedText").asText() : "");
            if (item.has("Confidence")) {
                double conf = item.get("Confidence").asDouble();
                if (conf < 90.0) sb.append(String.format(" [置信度:%.0f%%]", conf));
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    // ================================================================
    // 公共工具方法
    // ================================================================

    /**
     * 解析配置值：数据库三级作用域优先（自动解密），回退 yml 默认值。
     */
    private String resolveConfig(String configKey, String ymlDefault, RuntimeContext rc) {
        String userId = rc != null ? (String) rc.get("userId") : null;
        if (userId != null) {
            Long tenantId = rc.get("tenantId");
            String dbValue = configService.resolveValue(configKey, tenantId, userId);
            if (dbValue != null && !dbValue.isBlank()) {
                return configService.decryptValue(dbValue);
            }
        }
        return ymlDefault;
    }

    // ---- 密码学工具 ----

    private static byte[] hmac256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(String data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return bytesToHex(digest.digest(data.getBytes(StandardCharsets.UTF_8)));
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}
