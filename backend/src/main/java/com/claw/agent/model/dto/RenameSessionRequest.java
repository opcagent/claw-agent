package com.claw.agent.model.dto;

import lombok.Data;

/**
 * 会话重命名请求 DTO。
 */
@Data
public class RenameSessionRequest {

    /** 新标题（不可为空，最大 60 字符） */
    private String title;
}
