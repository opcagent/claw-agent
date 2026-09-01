package com.claw.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.claw.agent.model.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天消息 Mapper（MyBatis Plus）。
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
