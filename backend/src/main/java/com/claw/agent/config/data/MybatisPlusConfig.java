package com.claw.agent.config.data;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis Plus 配置。
 * <ul>
 *   <li>@MapperScan 扫描 mapper 包，注册所有 BaseMapper 子类</li>
 *   <li>分页插件：用户列表等查询使用</li>
 * </ul>
 */
@Configuration
@MapperScan("com.claw.agent.mapper")
public class MybatisPlusConfig {

    /**
     * 注册 MyBatis Plus 拦截器链（当前仅分页拦截器）。
     *
     * @return 分页拦截器配置好的 MybatisPlusInterceptor
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 分页拦截器，指定数据库类型为 MySQL
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        // 单页最大条数限制，防止恶意大分页拖垮数据库
        pagination.setMaxLimit(500L);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
