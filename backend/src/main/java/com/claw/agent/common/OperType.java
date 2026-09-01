package com.claw.agent.common;

/**
 * 操作日志业务类型（落库存枚举名）。
 */
public enum OperType {

    /** 新增 */
    CREATE,

    /** 修改 */
    UPDATE,

    /** 删除 */
    DELETE,

    /** 授权/分配类 */
    GRANT,

    /** 其他 */
    OTHER
}
