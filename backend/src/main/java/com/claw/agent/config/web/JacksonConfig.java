package com.claw.agent.config.web;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 全局序列化配置：统一前后端时间格式。
 * <p>
 * 请求与响应中的 JSR-310 时间类型一律使用「年-月-日 时:分:秒」风格，
 * 避免默认 ISO-8601（带 T 分隔符与毫秒尾）导致的各处手工 replace/裁剪；
 * 序列化与反序列化使用同一格式，保证请求体解析与响应下发对称。
 * <p>
 * WebFlux 的 JSON 编解码器与 SSE 事件 data 均使用容器内唯一的 ObjectMapper，
 * 该定制器对所有通道（含 {@code Result<T>} 与 {@code ChatEvent}）全局生效。
 */
@Configuration
public class JacksonConfig {

    /** 日期时间统一格式 */
    public static final String DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    /** 日期统一格式 */
    public static final String DATE_PATTERN = "yyyy-MM-dd";
    /** 时间统一格式 */
    public static final String TIME_PATTERN = "HH:mm:ss";

    /**
     * 注册 JSR-310 时间类型的统一序列化/反序列化格式。
     *
     * @return ObjectMapper 定制器（由 Spring Boot 自动应用到全局 ObjectMapper）
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer dateTimeJacksonCustomizer() {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(DATETIME_PATTERN);
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(DATE_PATTERN);
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern(TIME_PATTERN);
        return builder -> builder
                .serializers(
                        new LocalDateTimeSerializer(dateTimeFormatter),
                        new LocalDateSerializer(dateFormatter),
                        new LocalTimeSerializer(timeFormatter))
                .deserializers(
                        new LocalDateTimeDeserializer(dateTimeFormatter),
                        new LocalDateDeserializer(dateFormatter),
                        new LocalTimeDeserializer(timeFormatter));
    }
}
