package com.claw.agent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.claw.agent.model.ScheduledTask;
import com.claw.agent.model.ScheduledTaskLog;
import com.claw.agent.security.LoginUser;

import java.util.List;

/**
 * 定时任务服务：CRUD + 启停 + 执行。
 */
public interface ScheduledTaskService extends IService<ScheduledTask> {

    /**
     * 当前用户的任务列表。
     *
     * @param user 当前登录用户
     * @return 任务列表
     */
    List<ScheduledTask> listByUser(LoginUser user);

    /**
     * 新建任务（归属自动填充，自动计算下次执行时间）。
     *
     * @param user 当前登录用户
     * @param task 任务内容
     */
    void addTask(LoginUser user, ScheduledTask task);

    /**
     * 修改任务（只能改自己的，修改后重新计算下次执行时间）。
     *
     * @param user 当前登录用户
     * @param id   任务ID
     * @param task 更新内容
     */
    void updateTask(LoginUser user, Long id, ScheduledTask task);

    /**
     * 删除任务（只能删自己的）。
     *
     * @param user 当前登录用户
     * @param id   任务ID
     */
    void deleteTask(LoginUser user, Long id);

    /**
     * 切换启用/禁用状态。
     *
     * @param user 当前登录用户
     * @param id   任务ID
     */
    void toggleTask(LoginUser user, Long id);

    /**
     * 立即执行一次任务（手动触发，不影响定时调度）。
     *
     * @param user 当前登录用户
     * @param id   任务ID
     * @return 执行结果摘要
     */
    String runNow(LoginUser user, Long id);

    /**
     * 查询任务执行日志（按时间倒序）。
     *
     * @param user  当前登录用户
     * @param taskId 任务ID
     * @return 日志列表
     */
    List<ScheduledTaskLog> listLogs(LoginUser user, Long taskId);

    /**
     * 扫描并执行到期任务（由调度器每分钟调用）。
     */
    void scanAndExecuteDueTasks();
}
