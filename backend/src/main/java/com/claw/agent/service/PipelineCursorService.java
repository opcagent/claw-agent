package com.claw.agent.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 流水线游标服务：持久化流水线执行进度，支持断点续跑。
 * <p>
 * 核心机制：
 * <ul>
 *   <li>流水线启动时保存游标（pipelineCode + 总步数）到 Redis，TTL 24 小时</li>
 *   <li>每完成一个 ReAct 轮次（ModelCallEndEvent），更新游标的已完成步数</li>
 *   <li>用户在同一会话发新消息但未指定 pipelineCode 时，检测未完成游标并自动续跑</li>
 *   <li>续跑时重新注入剧本文本 + 标记已完成步骤，Agent 从断点继续执行</li>
 * </ul>
 * <p>
 * Redis 键：{@code pipeline:cursor:{userId}:{sessionId}}
 */
@Slf4j
@Service
public class PipelineCursorService {

    private static final String KEY_PREFIX = "pipeline:cursor:";
    private static final Duration CURSOR_TTL = Duration.ofHours(24);

    /** Redis 模板（可选：未安装 Redis 时为 null，游标功能自动降级为 no-op） */
    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    public PipelineCursorService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Redis 是否可用（未安装 Redis 时游标功能静默跳过，不阻断对话主流程） */
    private boolean isAvailable() {
        return redisTemplate != null;
    }

    /**
     * 保存流水线游标（流水线启动时调用）。
     *
     * @param userId      用户 ID
     * @param sessionId   会话 ID
     * @param pipelineCode 流水线编码
     * @param pipelineName 流水线名称
     * @param totalSteps  总步数
     */
    public void saveCursor(String userId, String sessionId, String pipelineCode,
                           String pipelineName, int totalSteps) {
        if (!isAvailable()) return;
        CursorData data = new CursorData();
        data.setPipelineCode(pipelineCode);
        data.setPipelineName(pipelineName);
        data.setTotalSteps(totalSteps);
        data.setCompletedSteps(0);
        writeCursor(userId, sessionId, data);
        log.info("流水线游标已保存: user={}, session={}, pipeline={}, totalSteps={}",
                userId, sessionId, pipelineCode, totalSteps);
    }

    /**
     * 更新游标进度（每完成一个 ReAct 轮次调用）。
     *
     * @param userId         用户 ID
     * @param sessionId      会话 ID
     * @param completedSteps 已完成的步数
     */
    public void updateProgress(String userId, String sessionId, int completedSteps) {
        if (!isAvailable()) return;
        CursorData data = loadCursor(userId, sessionId);
        if (data == null) {
            return;
        }
        if (completedSteps > data.getCompletedSteps()) {
            data.setCompletedSteps(completedSteps);
            writeCursor(userId, sessionId, data);
            log.debug("流水线游标进度更新: user={}, session={}, completedSteps={}/{}",
                    userId, sessionId, completedSteps, data.getTotalSteps());
        }
    }

    /**
     * 加载未完成游标（自动续跑检测时调用）。
     *
     * @return 游标数据，不存在或已过期返回 null
     */
    public CursorData loadCursor(String userId, String sessionId) {
        if (!isAvailable()) return null;
        String key = buildKey(userId, sessionId);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (!StringUtils.hasText(json)) {
                return null;
            }
            return objectMapper.readValue(json, CursorData.class);
        } catch (JsonProcessingException e) {
            log.warn("解析流水线游标失败: key={}", key, e);
            return null;
        }
    }

    /**
     * 清除游标（新流水线启动或会话结束时调用）。
     */
    public void clearCursor(String userId, String sessionId) {
        if (!isAvailable()) return;
        String key = buildKey(userId, sessionId);
        try {
            redisTemplate.delete(key);
            log.debug("流水线游标已清除: key={}", key);
        } catch (Exception e) {
            log.warn("清除流水线游标失败: key={}", key, e);
        }
    }

    /**
     * 判断游标是否表示流水线未完成。
     *
     * @param data 游标数据
     * @return true 表示流水线尚未完成所有步骤
     */
    public boolean isUnfinished(CursorData data) {
        return data != null && data.getCompletedSteps() < data.getTotalSteps();
    }

    private void writeCursor(String userId, String sessionId, CursorData data) {
        String key = buildKey(userId, sessionId);
        try {
            String json = objectMapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(key, json, CURSOR_TTL.toSeconds(), TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.warn("序列化流水线游标失败: key={}", key, e);
        }
    }

    private String buildKey(String userId, String sessionId) {
        return KEY_PREFIX + userId + ":" + sessionId;
    }

    /**
     * 游标数据结构（JSON 存储在 Redis 中）。
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CursorData {
        /** 流水线编码 */
        private String pipelineCode;
        /** 流水线名称（续跑时展示用） */
        private String pipelineName;
        /** 总步数 */
        private int totalSteps;
        /** 已完成的步数（基于 ModelCallEndEvent 计数） */
        private int completedSteps;
    }
}
