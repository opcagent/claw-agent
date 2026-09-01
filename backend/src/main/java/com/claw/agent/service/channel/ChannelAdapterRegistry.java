package com.claw.agent.service.channel;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 渠道适配器注册中心：自动收集所有 ChannelAdapter 实现 Bean，按 channelType 路由分发。
 * <p>
 * 新增渠道只需：
 * 1. 实现 ChannelAdapter 接口并标注 @Component
 * 2. 无需修改任何路由代码，Spring 自动注入
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelAdapterRegistry {

    private final List<ChannelAdapter> adapters;

    /** channelType → adapter 映射表 */
    private final Map<String, ChannelAdapter> adapterMap = new HashMap<>();

    /**
     * 初始化：遍历所有 ChannelAdapter Bean，按 channelType 注册。
     */
    @PostConstruct
    public void init() {
        for (ChannelAdapter adapter : adapters) {
            String type = adapter.getChannelType();
            if (adapterMap.containsKey(type)) {
                log.warn("渠道类型 {} 存在重复适配器：{} 和 {}，以先注册为准",
                        type, adapterMap.get(type).getClass().getSimpleName(),
                        adapter.getClass().getSimpleName());
                continue;
            }
            adapterMap.put(type, adapter);
            log.info("注册渠道适配器：{} → {}", type, adapter.getClass().getSimpleName());
        }
        log.info("渠道适配器注册完成，共 {} 个", adapterMap.size());
    }

    /**
     * 根据渠道类型获取对应的适配器。
     *
     * @param channelType 渠道类型（wechat / slack / telegram 等）
     * @return 对应的适配器
     * @throws IllegalArgumentException 该渠道类型未注册适配器
     */
    public ChannelAdapter getAdapter(String channelType) {
        ChannelAdapter adapter = adapterMap.get(channelType);
        if (adapter == null) {
            throw new IllegalArgumentException("未注册的渠道类型：" + channelType);
        }
        return adapter;
    }

    /**
     * 判断指定渠道类型是否已注册适配器。
     *
     * @param channelType 渠道类型
     * @return 是否已注册
     */
    public boolean hasAdapter(String channelType) {
        return adapterMap.containsKey(channelType);
    }

    /**
     * 获取所有已注册的渠道类型列表。
     *
     * @return 渠道类型集合
     */
    public java.util.Set<String> getRegisteredTypes() {
        return adapterMap.keySet();
    }
}
