package com.claw.agent.common;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Spring 上下文静态持有器。
 * <p>
 * 仅供无法注入 Bean 的静态工具（如 {@code ReactiveSupport} 记录操作日志）
 * 按类型获取 Bean 使用；常规代码一律构造器注入。
 */
@Component
public class SpringContextHolder implements ApplicationContextAware {

    private static ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        SpringContextHolder.context = applicationContext;
    }

    /**
     * 按类型获取 Bean。
     *
     * @param clazz Bean 类型
     * @param <T>   Bean 泛型
     * @return Bean 实例
     */
    public static <T> T getBean(Class<T> clazz) {
        return context.getBean(clazz);
    }
}
