package com.claw.agent.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户-角色关联实体（表 sys_user_role，多对多）。
 * <p>
 * 单列自增主键 + (user_id, role_id) 唯一键（MyBatis Plus 要求实体有主键），
 * 仅承载关联关系，登录时由它聚合用户的全部角色（roleKey 写入 JWT）。
 */
@Data
@TableName("sys_user_role")
public class UserRole {

    /** 主键ID（自增，insert 时留空由数据库生成） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID（格式：租户编码_自增序号） */
    private String userId;

    /** 角色ID */
    private Long roleId;

    /** 创建时间（库默认值填充，业务不传） */
    private LocalDateTime createTime;
}
