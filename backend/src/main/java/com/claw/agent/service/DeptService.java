package com.claw.agent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.claw.agent.model.Dept;
import com.claw.agent.security.LoginUser;

import java.util.List;

/**
 * 部门管理服务接口（租户内）。
 * <p>
 * 继承 {@link IService} 获得 MyBatis Plus 通用 CRUD 能力；
 * 业务方法（ancestors 父链维护、删除保护）声明如下，
 * 实现见 {@code impl/DeptServiceImpl}。
 */
public interface DeptService extends IService<Dept> {

    /**
     * 本租户部门列表（扁平，按 orderNum 升序，前端组树）。
     *
     * @param current 当前登录用户（决定租户）
     * @return 部门列表
     */
    List<Dept> listDepts(LoginUser current);

    /**
     * 新增部门（按父部门计算 ancestors 父链，默认启用）。
     *
     * @param current 当前登录用户（决定租户）
     * @param dept    部门信息
     */
    void addDept(LoginUser current, Dept dept);

    /**
     * 修改部门（调整父级时同步刷新自身与全部子孙的 ancestors）。
     *
     * @param current 当前登录用户
     * @param id      部门ID
     * @param dept    更新内容
     */
    void updateDept(LoginUser current, Long id, Dept dept);

    /**
     * 删除部门（存在子部门或挂用户时禁止）。
     *
     * @param current 当前登录用户
     * @param id      部门ID
     */
    void deleteDept(LoginUser current, Long id);
}
