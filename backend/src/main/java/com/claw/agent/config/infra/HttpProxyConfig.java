package com.claw.agent.config.infra;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * HTTP 代理配置。
 * <p>
 * 用于需要访问外网的服务（如联网搜索），在中国大陆网络环境下建议配置代理。
 * 配置方式：
 * <ul>
 *   <li>环境变量：HTTP_PROXY_HOST / HTTP_PROXY_PORT</li>
 *   <li>application.yml: claw.proxy.http.host / claw.proxy.http.port</li>
 * </ul>
 */
@Data
@Component
@ConfigurationProperties(prefix = "claw.proxy")
public class HttpProxyConfig {

    /** HTTP 代理主机地址（如：127.0.0.1） */
    private Http http = new Http();

    @Data
    public static class Http {
        private String host;
        private Integer port;
    }

    /** 是否配置了有效的代理 */
    public boolean isConfigured() {
        return http.getHost() != null && !http.getHost().isEmpty() 
                && http.getPort() != null && http.getPort() > 0;
    }

    /** 获取代理地址字符串（用于日志） */
    public String getProxyAddress() {
        if (isConfigured()) {
            return http.getHost() + ":" + http.getPort();
        }
        return "未配置";
    }
}
