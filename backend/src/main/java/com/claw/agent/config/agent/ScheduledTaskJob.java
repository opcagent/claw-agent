package com.claw.agent.config.agent;

import com.claw.agent.service.ScheduledTaskService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.context.ApplicationContext;

/**
 * Quartz 定时任务执行器：由 Quartz Scheduler 按 Cron 触发，委托 {@link ScheduledTaskService} 完成实际执行。
 * <p>
 * Quartz 自行创建 Job 实例（非 Spring 管理），因此通过静态 {@link ApplicationContext} 获取 Spring Bean。
 * {@link QuartzSchedulerConfig} 在启动时注入 context。
 */
@Slf4j
public class ScheduledTaskJob implements Job {

    /** Quartz JobDataMap 中存放任务 ID 的键 */
    public static final String TASK_ID_KEY = "taskId";

    /** Spring 应用上下文（由 QuartzSchedulerConfig 注入，volatile 保证 Quartz 线程可见性） */
    private static volatile ApplicationContext applicationContext;

    /**
     * 设置 Spring 应用上下文（仅由 QuartzSchedulerConfig 调用一次）。
     *
     * @param ctx Spring 应用上下文
     */
    public static void setApplicationContext(ApplicationContext ctx) {
        applicationContext = ctx;
    }

    @Override
    public void execute(JobExecutionContext context) {
        long taskId = context.getJobDetail().getJobDataMap().getLong(TASK_ID_KEY);
        log.debug("Quartz 触发定时任务: taskId={}, fireTime={}", taskId, context.getFireTime());

        if (applicationContext == null) {
            log.error("Spring ApplicationContext 未注入，无法执行定时任务 taskId={}", taskId);
            return;
        }

        try {
            ScheduledTaskService taskService = applicationContext.getBean(ScheduledTaskService.class);
            taskService.executeScheduledTask(taskId);
        } catch (Exception e) {
            log.error("定时任务执行异常: taskId={}", taskId, e);
        }
    }
}
