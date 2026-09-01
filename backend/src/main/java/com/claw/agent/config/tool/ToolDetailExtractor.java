package com.claw.agent.config.tool;

import io.agentscope.core.tool.Tool;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

/**
 * 工具详情提取器。
 * <p>
 * 从工具类中提取 @Tool 注解的方法,生成工具详情列表。
 */
@Slf4j
public class ToolDetailExtractor {

    /**
     * 工具详情。
     */
    @Data
    public static class ToolDetail {
        private String name;
        private String description;
        private List<ToolParameter> parameters;
        private String returnType;
    }

    /**
     * 工具参数。
     */
    @Data
    public static class ToolParameter {
        private String name;
        private String type;
        private String description;
        private boolean required;
    }

    /**
     * 从工具类中提取所有工具详情。
     *
     * @param toolClass 工具类
     * @return 工具详情列表
     */
    public static List<ToolDetail> extractTools(Class<?> toolClass) {
        List<ToolDetail> tools = new ArrayList<>();
        
        for (Method method : toolClass.getDeclaredMethods()) {
            Tool toolAnnotation = method.getAnnotation(Tool.class);
            if (toolAnnotation != null) {
                ToolDetail detail = new ToolDetail();
                detail.setName(toolAnnotation.name());
                detail.setDescription(toolAnnotation.description());
                detail.setReturnType(method.getReturnType().getSimpleName());
                
                // 提取参数信息
                List<ToolParameter> params = new ArrayList<>();
                Parameter[] parameters = method.getParameters();
                for (Parameter param : parameters) {
                    ToolParameter tp = new ToolParameter();
                    tp.setName(param.getName());
                    tp.setType(param.getType().getSimpleName());
                    tp.setRequired(true); // 默认必填
                    params.add(tp);
                }
                detail.setParameters(params);
                
                tools.add(detail);
            }
        }
        
        return tools;
    }
}
