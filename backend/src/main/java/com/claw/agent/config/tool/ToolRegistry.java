package com.claw.agent.config.tool;

import com.claw.agent.tool.annotation.ToolSet;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 工具集自动扫描与注册器。
 * <p>
 * 启动时自动扫描 {@code com.claw.agent.tool} 包下所有带 {@link ToolSet} 注解的类，
 * 提取元数据并注册到运行时注册表，支持动态启用/禁用。
 * 支持通过 {@link ToolFactory} 自定义工具实例化逻辑。
 */
@Slf4j
@Component
public class ToolRegistry {

    /** 工具集元数据缓存（code -> metadata） */
    private final Map<String, ToolMetadata> toolMetadataCache = new HashMap<>();

    /** 已启用的工具集代码列表 */
    private final Set<String> enabledToolCodes = new HashSet<>();
    
    /** 工具工厂列表（用于需要特殊参数的工具） */
    private final List<ToolFactory<?>> toolFactories = new ArrayList<>();
    
    /** 工具详情缓存（code -> tools） */
    private final Map<String, List<ToolDetailExtractor.ToolDetail>> toolDetailsCache = new HashMap<>();

    /** 无状态工具实例缓存（code -> instance），避免每次 Agent 构建都反射创建新实例 */
    private final Map<String, Object> toolInstanceCache = new ConcurrentHashMap<>();

    /**
     * 工具集元数据。
     */
    @Data
    public static class ToolMetadata {
        private String code;
        private String name;
        private String description;
        private String category;
        private boolean enabledByDefault;
        private String version;
        private List<String> dependencies;
        private boolean requiresHITL;
        private List<String> allowedRoles;
        private Class<?> toolClass;

        public ToolMetadata(ToolSet annotation, Class<?> toolClass) {
            this.code = annotation.code();
            this.name = annotation.name();
            this.description = annotation.description();
            this.category = annotation.category();
            this.enabledByDefault = annotation.enabledByDefault();
            this.version = annotation.version();
            this.dependencies = Arrays.asList(annotation.dependencies());
            this.requiresHITL = annotation.requiresHITL();
            this.allowedRoles = Arrays.asList(annotation.allowedRoles());
            this.toolClass = toolClass;
        }
    }

    /**
     * 初始化：扫描并注册所有工具集。
     */
    public void initialize() {
        log.info("开始扫描工具集...");
        
        // 1. 扫描 com.claw.agent.tool 包下的所有类
        ClassPathScanningCandidateComponentProvider scanner = 
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(ToolSet.class));
        
        Set<BeanDefinition> candidates = scanner.findCandidateComponents("com.claw.agent.tool");
        log.info("发现 {} 个工具集候选类", candidates.size());
        
        // 2. 提取元数据并缓存
        for (BeanDefinition bd : candidates) {
            try {
                Class<?> clazz = Class.forName(bd.getBeanClassName());
                ToolSet annotation = clazz.getAnnotation(ToolSet.class);
                
                if (annotation != null) {
                    ToolMetadata metadata = new ToolMetadata(annotation, clazz);
                    toolMetadataCache.put(metadata.getCode(), metadata);
                    
                    // 提取工具详情并缓存
                    try {
                        List<ToolDetailExtractor.ToolDetail> tools = 
                            ToolDetailExtractor.extractTools(clazz);
                        toolDetailsCache.put(metadata.getCode(), tools);
                        log.debug("  └─ 包含 {} 个工具: {}", tools.size(), 
                            tools.stream().map(t -> t.getName()).collect(Collectors.joining(", ")));
                    } catch (Exception e) {
                        log.warn("  └─ 提取工具详情失败: {}", e.getMessage());
                        toolDetailsCache.put(metadata.getCode(), new ArrayList<>());
                    }
                    
                    // 默认启用的工具集加入启用列表
                    if (metadata.isEnabledByDefault()) {
                        enabledToolCodes.add(metadata.getCode());
                    }
                    
                    log.info("✓ 注册工具集: {} ({}) - {}", 
                            metadata.getName(), metadata.getCode(), metadata.getDescription());
                }
            } catch (ClassNotFoundException e) {
                log.error("加载工具集类失败: {}", bd.getBeanClassName(), e);
            }
        }
        
        log.info("工具集扫描完成，共注册 {} 个工具集，默认启用 {} 个", 
                toolMetadataCache.size(), enabledToolCodes.size());
    }

    /**
     * 获取所有工具集元数据。
     *
     * @return 工具集元数据列表
     */
    public List<ToolMetadata> getAllToolSets() {
        return new ArrayList<>(toolMetadataCache.values());
    }

    /**
     * 获取指定工具集元数据。
     *
     * @param code 工具集代码
     * @return 工具集元数据，不存在返回 null
     */
    public ToolMetadata getToolSet(String code) {
        return toolMetadataCache.get(code);
    }

    /**
     * 检查工具集是否已启用。
     *
     * @param code 工具集代码
     * @return true 表示已启用
     */
    public boolean isToolSetEnabled(String code) {
        return enabledToolCodes.contains(code);
    }

    /**
     * 启用工具集。
     *
     * @param code 工具集代码
     */
    public void enableToolSet(String code) {
        ToolMetadata metadata = toolMetadataCache.get(code);
        if (metadata == null) {
            throw new IllegalArgumentException("工具集不存在: " + code);
        }
        
        // 检查依赖
        for (String dep : metadata.getDependencies()) {
            if (!enabledToolCodes.contains(dep)) {
                throw new IllegalStateException(
                        String.format("工具集 %s 依赖的工具集 %s 未启用", code, dep));
            }
        }
        
        enabledToolCodes.add(code);
        log.info("工具集已启用: {} ({})", metadata.getName(), code);
    }

    /**
     * 禁用工具集。
     *
     * @param code 工具集代码
     */
    public void disableToolSet(String code) {
        ToolMetadata metadata = toolMetadataCache.get(code);
        if (metadata == null) {
            throw new IllegalArgumentException("工具集不存在: " + code);
        }
        
        // 检查是否有其他工具集依赖此工具集
        for (Map.Entry<String, ToolMetadata> entry : toolMetadataCache.entrySet()) {
            if (entry.getValue().getDependencies().contains(code) && 
                enabledToolCodes.contains(entry.getKey())) {
                throw new IllegalStateException(
                        String.format("工具集 %s 被 %s 依赖，无法禁用", code, entry.getKey()));
            }
        }
        
        enabledToolCodes.remove(code);
        log.info("工具集已禁用: {} ({})", metadata.getName(), code);
    }

    /**
     * 获取已启用的工具集代码列表。
     *
     * @return 已启用的工具集代码集合
     */
    public Set<String> getEnabledToolCodes() {
        return Collections.unmodifiableSet(enabledToolCodes);
    }

    /**
     * 按分类获取工具集。
     *
     * @param category 分类名称
     * @return 该分类下的工具集列表
     */
    public List<ToolMetadata> getToolSetsByCategory(String category) {
        return toolMetadataCache.values().stream()
                .filter(m -> m.getCategory().equals(category))
                .collect(Collectors.toList());
    }

    /**
     * 获取所有工具集的完整信息（包含工具详情）。
     *
     * @return 工具集元数据列表，每个包含 tools 字段
     */
    public List<ToolMetadataWithDetails> getAllToolSetsWithDetails() {
        return toolMetadataCache.values().stream()
                .map(metadata -> {
                    ToolMetadataWithDetails withDetails = new ToolMetadataWithDetails();
                    withDetails.setCode(metadata.getCode());
                    withDetails.setName(metadata.getName());
                    withDetails.setDescription(metadata.getDescription());
                    withDetails.setCategory(metadata.getCategory());
                    withDetails.setEnabledByDefault(metadata.isEnabledByDefault());
                    withDetails.setVersion(metadata.getVersion());
                    withDetails.setDependencies(metadata.getDependencies());
                    withDetails.setRequiresHITL(metadata.isRequiresHITL());
                    withDetails.setAllowedRoles(metadata.getAllowedRoles());
                    withDetails.setEnabled(enabledToolCodes.contains(metadata.getCode()));
                    withDetails.setTools(toolDetailsCache.getOrDefault(metadata.getCode(), new ArrayList<>()));
                    return withDetails;
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取指定工具集的工具详情列表。
     *
     * @param code 工具集代码
     * @return 工具详情列表
     */
    public List<ToolDetailExtractor.ToolDetail> getToolDetails(String code) {
        return toolDetailsCache.getOrDefault(code, new ArrayList<>());
    }

    /**
     * 带工具详情的工具集元数据。
     */
    @lombok.Data
    public static class ToolMetadataWithDetails {
        private String code;
        private String name;
        private String description;
        private String category;
        private boolean enabledByDefault;
        private String version;
        private List<String> dependencies;
        private boolean requiresHITL;
        private List<String> allowedRoles;
        private boolean enabled;
        private List<ToolDetailExtractor.ToolDetail> tools;
    }

    /**
     * 注册工具工厂。
     *
     * @param factory 工具工厂
     */
    public void registerFactory(ToolFactory<?> factory) {
        toolFactories.add(factory);
        log.info("已注册工具工厂: {}", factory.getClass().getSimpleName());
    }

    /**
     * 获取或创建工具实例（无状态工具全局缓存，避免重复反射创建）。
     * <p>
     * 首次调用时通过工厂或反射创建实例并缓存，后续调用直接返回缓存实例。
     * 仅适用于无状态工具（如 MultiSearchTools、ShellTools），有状态工具
     * （如 NoteTools 需要 workspace 参数）应由调用侧直接创建，不走此缓存。
     *
     * @param code 工具集代码
     * @return 共享的工具实例
     */
    public Object getOrCreateInstance(String code) {
        return toolInstanceCache.computeIfAbsent(code, this::instantiateTool);
    }

    /**
     * 实例化工具对象。
     * <p>
     * 优先使用注册的 ToolFactory，如果无匹配工厂则使用默认反射构造。
     *
     * @param code 工具集代码
     * @return 工具实例
     */
    public Object instantiateTool(String code) {
        ToolMetadata metadata = toolMetadataCache.get(code);
        if (metadata == null) {
            throw new IllegalArgumentException("工具集不存在: " + code);
        }
        
        // 1. 尝试使用注册的工厂
        for (ToolFactory<?> factory : toolFactories) {
            if (factory.supports(code)) {
                try {
                    Object tool = factory.create(metadata);
                    log.debug("使用工厂创建工具: code={}, factory={}", code, factory.getClass().getSimpleName());
                    return tool;
                } catch (Exception e) {
                    log.warn("工具工厂创建失败，尝试默认构造: code={}, factory={}", code, factory.getClass().getSimpleName(), e);
                    // 继续尝试下一个工厂或默认构造
                }
            }
        }
        
        // 2. 使用默认反射构造（无参构造函数）
        try {
            return metadata.getToolClass().getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException(
                String.format("实例化工具失败: %s (类=%s, 原因=%s)", 
                    code, metadata.getToolClass().getName(), e.getMessage()), e);
        }
    }
}
