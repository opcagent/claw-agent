package com.claw.agent.config.data;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.claw.agent.common.UserContextHolder;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis Plus 审计字段自动填充器。
 * <p>
 * 插入时填充 创建/更新时间、创建人/修改人（用户名 + ID），更新时填充 更新时间、修改人；
 * 操作人取自 {@link UserContextHolder}（由 ReactiveSupport 在阻塞线程执行期间写入）。
 */
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    /**
     * 插入填充：六个审计字段一次性写入。
     *
     * @param metaObject 元对象
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        String username = UserContextHolder.getUsername();
        String userId = UserContextHolder.getUserId();
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "creator", String.class, username);
        this.strictInsertFill(metaObject, "updater", String.class, username);
        this.strictInsertFill(metaObject, "creatorId", String.class, userId);
        this.strictInsertFill(metaObject, "updaterId", String.class, userId);
    }

    /**
     * 更新填充：刷新更新时间与修改人（用户名 + ID）。
     *
     * @param metaObject 元对象
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        this.strictUpdateFill(metaObject, "updater", String.class, UserContextHolder.getUsername());
        this.strictUpdateFill(metaObject, "updaterId", String.class, UserContextHolder.getUserId());
    }
}
