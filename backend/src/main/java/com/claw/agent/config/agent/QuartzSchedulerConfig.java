package com.claw.agent.config.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.claw.agent.mapper.ScheduledTaskMapper;
import com.claw.agent.model.ScheduledTask;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Properties;

/**
 * Quartz 调度器配置：创建嵌入式 Scheduler，启动时注册所有已启用的定时任务。
 * <p>
 * 使用 RAMJobStore（内存存储）：单实例场景足够，重启后由 {@code registerAllEnabledTasks()} 从数据库重建。
 * 多实例部署时可切换为 JDBC JobStore + 集群模式，实现分布式协调（任务不重复执行）。
 */
@Slf4j
@Configuration
public class QuartzSchedulerConfig {

    private static final String JOB_GROUP = "claw-agent-tasks";
    private static final String TRIGGER_GROUP = "claw-agent-triggers";

    private Scheduler scheduler;

    /**
     * 创建并启动 Quartz Scheduler（RAMJobStore 内存模式）。
     *
     * @param applicationContext Spring 上下文（注入到 ScheduledTaskJob 静态字段）
     * @param taskMapper         任务 Mapper（启动时加载已启用任务）
     * @return Quartz Scheduler Bean
     */
    @Bean
    public Scheduler quartzScheduler(ApplicationContext applicationContext,
                                     ScheduledTaskMapper taskMapper) throws SchedulerException {
        // Quartz 基础配置：RAMJobStore + 线程池
        Properties props = new Properties();
        props.setProperty("org.quartz.scheduler.instanceName", "ClawAgentScheduler");
        props.setProperty("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
        props.setProperty("org.quartz.threadPool.threadCount", "3");
        // 关闭 XML 反序列化安全限制（Quartz 2.5+ 默认拒绝所有，需显式放行 Job 类）
        props.setProperty("org.quartz.scheduler.classLoadHelper.class",
                "org.quartz.simpl.CascadingClassLoadHelper");

        StdSchedulerFactory factory = new StdSchedulerFactory();
        factory.initialize(props);
        scheduler = factory.getScheduler();

        // 注入 Spring 上下文到 Job 静态字段（Job 实例由 Quartz 创建，无法通过构造器注入）
        ScheduledTaskJob.setApplicationContext(applicationContext);

        // 启动调度器
        scheduler.start();
        log.info("Quartz Scheduler 已启动");

        // 注册数据库中所有已启用的定时任务
        registerAllEnabledTasks(taskMapper);

        return scheduler;
    }

    /**
     * 从数据库加载所有已启用任务，注册为 Quartz Job。
     */
    private void registerAllEnabledTasks(ScheduledTaskMapper taskMapper) {
        List<ScheduledTask> enabledTasks = taskMapper.selectList(
                new LambdaQueryWrapper<ScheduledTask>()
                        .eq(ScheduledTask::getEnabled, 1));

        int registered = 0;
        for (ScheduledTask task : enabledTasks) {
            try {
                scheduleTask(task);
                registered++;
            } catch (Exception e) {
                log.warn("注册定时任务失败: taskId={}, cron={}", task.getId(), task.getCronExpr(), e);
            }
        }
        log.info("已注册 {} 个定时任务到 Quartz Scheduler", registered);
    }

    /**
     * 将单个任务注册为 Quartz Job（Cron 触发）。
     *
     * @param task 定时任务实体
     */
    public void scheduleTask(ScheduledTask task) throws SchedulerException {
        if (scheduler == null) {
            log.warn("Scheduler 未初始化，跳过注册: taskId={}", task.getId());
            return;
        }

        String jobKey = jobKey(task.getId());
        String triggerKey = triggerKey(task.getId());

        // 构建 JobDetail：传递任务 ID
        JobDetail jobDetail = JobBuilder.newJob(ScheduledTaskJob.class)
                .withIdentity(jobKey, JOB_GROUP)
                .usingJobData(ScheduledTaskJob.TASK_ID_KEY, task.getId())
                .storeDurably(true)
                .build();

        // 构建 CronTrigger
        String normalizedCron = normalizeCron(task.getCronExpr());
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey, TRIGGER_GROUP)
                .withSchedule(CronScheduleBuilder.cronSchedule(normalizedCron)
                        .withMisfireHandlingInstructionDoNothing())
                .build();

        scheduler.scheduleJob(jobDetail, trigger);
        log.info("注册 Quartz 任务: taskId={}, cron={}", task.getId(), normalizedCron);
    }

    /**
     * 取消任务的 Quartz 调度。
     *
     * @param taskId 任务 ID
     */
    public void unscheduleTask(Long taskId) throws SchedulerException {
        if (scheduler == null) return;

        JobKey jobKey = new JobKey(jobKey(taskId), JOB_GROUP);
        if (scheduler.checkExists(jobKey)) {
            scheduler.deleteJob(jobKey);
            log.info("取消 Quartz 任务: taskId={}", taskId);
        }
    }

    /**
     * 重新调度任务（更新 Cron 表达式）。
     *
     * @param task 更新后的任务实体
     */
    public void rescheduleTask(ScheduledTask task) throws SchedulerException {
        if (scheduler == null) return;

        TriggerKey triggerKey = new TriggerKey(triggerKey(task.getId()), TRIGGER_GROUP);
        if (scheduler.checkExists(triggerKey)) {
            String normalizedCron = normalizeCron(task.getCronExpr());
            Trigger newTrigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey)
                    .withSchedule(CronScheduleBuilder.cronSchedule(normalizedCron)
                            .withMisfireHandlingInstructionDoNothing())
                    .build();
            scheduler.rescheduleJob(triggerKey, newTrigger);
            log.info("更新 Quartz 任务 Cron: taskId={}, cron={}", task.getId(), normalizedCron);
        } else {
            // Trigger 不存在（可能之前被禁用），重新注册
            scheduleTask(task);
        }
    }

    /**
     * 应用关闭时停止 Scheduler。
     */
    @PreDestroy
    public void shutdown() {
        try {
            if (scheduler != null && !scheduler.isShutdown()) {
                scheduler.shutdown(true);
                log.info("Quartz Scheduler 已关闭");
            }
        } catch (SchedulerException e) {
            log.error("关闭 Quartz Scheduler 失败", e);
        }
    }

    /** Quartz JobKey 命名：taskId 转字符串 */
    private static String jobKey(Long taskId) {
        return "task-" + taskId;
    }

    /** Quartz TriggerKey 命名：taskId 转字符串 */
    private static String triggerKey(Long taskId) {
        return "trigger-" + taskId;
    }

    /**
     * 5 位标准 Cron → 6 位 Quartz Cron（补秒字段 0）。
     * <p>
     * Quartz 2.5+ 支持 5/6/7 位格式，但为与前端统一，5 位补秒字段。
     */
    private static String normalizeCron(String cronExpr) {
        if (cronExpr == null) return "0 0 * * * ?";
        String[] parts = cronExpr.trim().split("\\s+");
        if (parts.length == 5) {
            return "0 " + cronExpr.trim();
        }
        return cronExpr.trim();
    }
}
