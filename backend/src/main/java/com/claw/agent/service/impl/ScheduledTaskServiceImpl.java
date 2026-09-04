package com.claw.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.claw.agent.common.BizException;
import com.claw.agent.common.ResultCode;
import com.claw.agent.common.UserContextHolder;
import com.claw.agent.config.agent.QuartzSchedulerConfig;
import com.claw.agent.mapper.ScheduledTaskLogMapper;
import com.claw.agent.mapper.ScheduledTaskMapper;
import com.claw.agent.model.ScheduledTask;
import com.claw.agent.model.ScheduledTaskLog;
import com.claw.agent.model.dto.ChatEvent;
import com.claw.agent.model.dto.ChatRequest;
import com.claw.agent.security.LoginUser;
import com.claw.agent.service.AgentService;
import com.claw.agent.service.EmailService;
import com.claw.agent.service.ScheduledTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务服务实现：CRUD + Quartz 调度 + 日志记录。
 * <p>
 * 调度由 AgentScope Quartz 扩展驱动（{@link QuartzSchedulerConfig}），
 * 核心执行逻辑：模拟用户身份 → 构建 ChatRequest → 调用 AgentService → 收集结果 → 可选邮件通知。
 * Cron 解析使用 Spring 内置的 {@link CronExpression}（支持 6 位 Spring 格式，兼容 5 位标准格式）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledTaskServiceImpl extends ServiceImpl<ScheduledTaskMapper, ScheduledTask> implements ScheduledTaskService {

    private final ScheduledTaskLogMapper taskLogMapper;
    private final AgentService agentService;
    private final EmailService emailService;
    private final QuartzSchedulerConfig quartzScheduler;

    @Override
    public List<ScheduledTask> listByUser(LoginUser user) {
        return baseMapper.selectList(new LambdaQueryWrapper<ScheduledTask>()
                .eq(ScheduledTask::getUserId, user.getUserId())
                .orderByDesc(ScheduledTask::getCreateTime));
    }

    @Override
    public void addTask(LoginUser user, ScheduledTask task) {
        if (!StringUtils.hasText(task.getTaskName()) || task.getTaskName().isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "任务名称不能为空");
        }
        if (!StringUtils.hasText(task.getCronExpr()) || task.getCronExpr().isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "Cron 表达式不能为空");
        }
        if (!StringUtils.hasText(task.getPromptContent()) || task.getPromptContent().isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "Prompt 内容不能为空");
        }
        // 校验 Cron 表达式合法性
        parseCron(task.getCronExpr());

        task.setTenantId(user.getTenantId());
        task.setUserId(user.getUserId());
        task.setUsername(user.getUsername());
        task.setEnabled(task.getEnabled() == null ? 1 : task.getEnabled());
        task.setNextRunTime(calculateNextRunTime(task.getCronExpr()));
        baseMapper.insert(task);
        // 启用状态时注册到 Quartz Scheduler
        if (Integer.valueOf(1).equals(task.getEnabled())) {
            try {
                quartzScheduler.scheduleTask(task);
            } catch (Exception e) {
                log.warn("注册 Quartz 任务失败: taskId={}", task.getId(), e);
            }
        }
    }

    @Override
    public void updateTask(LoginUser user, Long id, ScheduledTask task) {
        ScheduledTask existing = getAndCheckOwnership(user, id);
        if (StringUtils.hasText(task.getTaskName())) {
            existing.setTaskName(task.getTaskName());
        }
        if (StringUtils.hasText(task.getCronExpr())) {
            parseCron(task.getCronExpr()); // 校验
            existing.setCronExpr(task.getCronExpr());
        }
        if (task.getPresetCode() != null) {
            existing.setPresetCode(task.getPresetCode());
        }
        if (task.getPipelineCode() != null) {
            existing.setPipelineCode(task.getPipelineCode());
        }
        if (StringUtils.hasText(task.getPromptContent())) {
            existing.setPromptContent(task.getPromptContent());
        }
        if (task.getNotifyEmail() != null) {
            existing.setNotifyEmail(task.getNotifyEmail());
        }
        // 重新计算下次执行时间
        existing.setNextRunTime(calculateNextRunTime(existing.getCronExpr()));
        baseMapper.updateById(existing);
        // 同步更新 Quartz 调度（Cron 可能变更）
        try {
            quartzScheduler.rescheduleTask(existing);
        } catch (Exception e) {
            log.warn("更新 Quartz 任务失败: taskId={}", existing.getId(), e);
        }
    }

    @Override
    public void deleteTask(LoginUser user, Long id) {
        getAndCheckOwnership(user, id);
        // 先从 Quartz 移除调度，再删数据库记录
        try {
            quartzScheduler.unscheduleTask(id);
        } catch (Exception e) {
            log.warn("取消 Quartz 任务失败: taskId={}", id, e);
        }
        baseMapper.deleteById(id);
    }

    @Override
    public void toggleTask(LoginUser user, Long id) {
        ScheduledTask existing = getAndCheckOwnership(user, id);
        boolean enabling = !Integer.valueOf(1).equals(existing.getEnabled());
        existing.setEnabled(enabling ? 1 : 0);
        if (enabling) {
            existing.setNextRunTime(calculateNextRunTime(existing.getCronExpr()));
        }
        baseMapper.updateById(existing);
        // 同步 Quartz 调度：启用 → 注册，禁用 → 移除
        try {
            if (enabling) {
                quartzScheduler.scheduleTask(existing);
            } else {
                quartzScheduler.unscheduleTask(id);
            }
        } catch (Exception e) {
            log.warn("切换 Quartz 任务状态失败: taskId={}, enabling={}", id, enabling, e);
        }
    }

    @Override
    public String runNow(LoginUser user, Long id) {
        ScheduledTask task = getAndCheckOwnership(user, id);
        return doExecuteTask(task);
    }

    @Override
    public List<ScheduledTaskLog> listLogs(LoginUser user, Long taskId) {
        // 校验任务归属
        getAndCheckOwnership(user, taskId);
        return taskLogMapper.selectList(new LambdaQueryWrapper<ScheduledTaskLog>()
                .eq(ScheduledTaskLog::getTaskId, taskId)
                .orderByDesc(ScheduledTaskLog::getRunTime)
                .last("LIMIT 50"));
    }

    @Override
    public void executeScheduledTask(long taskId) {
        ScheduledTask task = baseMapper.selectById(taskId);
        if (task == null) {
            log.warn("定时任务不存在: taskId={}", taskId);
            return;
        }
        if (!Integer.valueOf(1).equals(task.getEnabled())) {
            log.warn("定时任务已禁用，跳过执行: taskId={}", taskId);
            return;
        }
        // Quartz 线程为普通阻塞线程，可直接执行
        doExecuteTask(task);
    }

    /**
     * 核心执行逻辑：模拟用户身份调用 Agent 对话，收集结果并记录日志。
     */
    private String doExecuteTask(ScheduledTask task) {
        LocalDateTime runTime = LocalDateTime.now();
        String result = null;
        String errorMsg = null;
        String status = ScheduledTask.STATUS_SUCCESS;

        try {
            // 构建模拟用户身份（定时任务没有 HTTP 请求上下文，需手动构造 LoginUser）
            LoginUser simulatedUser = new LoginUser(
                    task.getUserId(),
                    task.getUsername(),
                    task.getTenantId(),
                    List.of("common") // 定时任务以普通用户权限执行
            );
            UserContextHolder.set(simulatedUser);

            // 构建聊天请求
            ChatRequest request = new ChatRequest();
            request.setContent(task.getPromptContent());
            request.setPresetCode(task.getPresetCode());
            request.setPipelineCode(task.getPipelineCode());

            // 调用 AgentService 并收集结果
            Flux<ChatEvent> eventFlux = agentService.chat(simulatedUser, request);
            StringBuilder resultBuilder = new StringBuilder();
            eventFlux.toStream().forEach(event -> {
                if ("text".equals(event.getType()) && event.getDelta() != null) {
                    resultBuilder.append(event.getDelta());
                }
            });
            result = resultBuilder.toString();

            // 邮件通知
            if (StringUtils.hasText(task.getNotifyEmail()) && StringUtils.hasText(result)) {
                sendNotification(task, result);
            }
        } catch (Exception e) {
            status = ScheduledTask.STATUS_FAIL;
            errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.length() > 500) {
                errorMsg = errorMsg.substring(0, 500);
            }
            log.error("定时任务执行失败: taskId={}, taskName={}", task.getId(), task.getTaskName(), e);
        } finally {
            UserContextHolder.clear();
        }

        // 记录执行日志
        ScheduledTaskLog logEntry = new ScheduledTaskLog();
        logEntry.setTaskId(task.getId());
        logEntry.setTenantId(task.getTenantId());
        logEntry.setStatus(status);
        logEntry.setResultText(result != null && result.length() > 10000 ? result.substring(0, 10000) + "..." : result);
        logEntry.setRunTime(runTime);
        logEntry.setErrorMsg(errorMsg);
        taskLogMapper.insert(logEntry);

        // 更新任务的上次/下次执行时间
        task.setLastRunTime(runTime);
        task.setNextRunTime(calculateNextRunTime(task.getCronExpr()));
        baseMapper.updateById(task);

        return result != null ? result : (errorMsg != null ? "执行失败: " + errorMsg : "无结果");
    }

    /**
     * 发送执行结果邮件通知。
     */
    private void sendNotification(ScheduledTask task, String result) {
        try {
            String subject = "[定时任务] " + task.getTaskName() + " 执行结果";
            // 截取前 500 字作为邮件内容摘要
            String content = result.length() > 500 ? result.substring(0, 500) + "..." : result;
            content = "<pre style='white-space: pre-wrap;'>" + content + "</pre>";
            emailService.sendEmail(
                    task.getUserId(),
                    task.getTenantId(),
                    task.getNotifyEmail(),
                    subject,
                    content,
                    true,
                    null
            );
        } catch (Exception e) {
            log.warn("定时任务邮件通知发送失败: taskId={}, email={}", task.getId(), task.getNotifyEmail(), e);
        }
    }

    /**
     * 查询并校验归属权：只能操作自己的任务。
     */
    private ScheduledTask getAndCheckOwnership(LoginUser user, Long id) {
        ScheduledTask task = baseMapper.selectById(id);
        if (task == null || !task.getUserId().equals(user.getUserId())) {
            throw new BizException(ResultCode.NOT_FOUND, "任务不存在或无权操作");
        }
        return task;
    }

    /**
     * 解析 Cron 表达式（兼容 5 位标准和 6 位 Spring 格式）。
     *
     * @throws BizException 表达式非法时抛出
     */
    private CronExpression parseCron(String cronExpr) {
        try {
            // Spring CronExpression 要求 6 位（含秒），5 位标准格式需补秒字段
            String normalized = normalizeCron(cronExpr);
            return CronExpression.isValidExpression(normalized)
                    ? CronExpression.parse(normalized)
                    : null;
        } catch (Exception e) {
            throw new BizException(ResultCode.PARAM_ERROR, "Cron 表达式格式错误: " + cronExpr);
        }
    }

    /**
     * 计算下次执行时间。
     */
    private LocalDateTime calculateNextRunTime(String cronExpr) {
        try {
            String normalized = normalizeCron(cronExpr);
            CronExpression cron = CronExpression.parse(normalized);
            return cron.next(LocalDateTime.now());
        } catch (Exception e) {
            log.warn("Cron 解析失败，使用默认 1 小时后: cron={}", cronExpr);
            return LocalDateTime.now().plusHours(1);
        }
    }

    /**
     * 5 位标准 Cron → 6 位 Spring Cron（补秒字段 0）。
     * <p>
     * 标准：分 时 日 月 周 → Spring：秒 分 时 日 月 周
     */
    private String normalizeCron(String cronExpr) {
        if (cronExpr == null) return "0 0 * * * *";
        String[] parts = cronExpr.trim().split("\\s+");
        if (parts.length == 5) {
            return "0 " + cronExpr.trim();
        }
        return cronExpr.trim();
    }
}
