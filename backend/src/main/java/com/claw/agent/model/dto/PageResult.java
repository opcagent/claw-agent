package com.claw.agent.model.dto;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.List;

/**
 * 通用分页视图（分页接口统一下发结构）。
 * <p>
 * MyBatis Plus 原生 Page 携带 orders / optimizeCountSql 等内部字段，
 * 直接下发既冗余又暴露实现，此处统一收窄为四字段视图。
 *
 * @param <T> 记录类型
 */
@Data
public class PageResult<T> {

    /** 当前页记录 */
    private List<T> records;

    /** 总记录数 */
    private long total;

    /** 当前页码（从 1 起） */
    private long pageNum;

    /** 每页条数 */
    private long pageSize;

    /**
     * 从 MyBatis Plus 分页结果构建视图。
     *
     * @param page MyBatis Plus 分页查询结果
     * @param <T>  记录类型
     * @return 收窄后的分页视图
     */
    public static <T> PageResult<T> from(IPage<T> page) {
        PageResult<T> r = new PageResult<>();
        r.records = page.getRecords();
        r.total = page.getTotal();
        r.pageNum = page.getCurrent();
        r.pageSize = page.getSize();
        return r;
    }
}
