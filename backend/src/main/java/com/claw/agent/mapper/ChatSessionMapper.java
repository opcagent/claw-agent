package com.claw.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.claw.agent.model.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 会话元数据 Mapper（MyBatis Plus）。
 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    /**
     * 插入或刷新会话：存在则更新 update_time，不存在则插入。
     * <p>
     * 利用唯一索引 uk_user_session(username, session_id)，单次 SQL 完成 SELECT + INSERT/UPDATE，
     * 避免每条消息都触发两次 DB 操作。
     *
     * @param tenantId 租户ID
     * @param sessionId 会话ID
     * @param username 用户名
     * @param title 标题（仅插入时使用）
     */
    void insertOrUpdate(@Param("tenantId") Long tenantId,
                        @Param("sessionId") String sessionId,
                        @Param("username") String username,
                        @Param("title") String title);
}
