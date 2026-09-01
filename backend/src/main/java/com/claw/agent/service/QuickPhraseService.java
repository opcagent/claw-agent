package com.claw.agent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.claw.agent.model.UserQuickPhrase;
import com.claw.agent.security.LoginUser;

import java.util.List;

/**
 * 用户常用语服务：CRUD + 按用户隔离查询。
 */
public interface QuickPhraseService extends IService<UserQuickPhrase> {

    /**
     * 当前用户的常用语列表（按 sort_order 升序）。
     *
     * @param user 当前登录用户
     * @return 常用语列表
     */
    List<UserQuickPhrase> listByUser(LoginUser user);

    /**
     * 新建常用语（归属自动填充）。
     *
     * @param user    当前登录用户
     * @param phrase  常用语内容
     */
    void addPhrase(LoginUser user, UserQuickPhrase phrase);

    /**
     * 修改常用语（只能改自己的）。
     *
     * @param user   当前登录用户
     * @param id     常用语ID
     * @param phrase 更新内容
     */
    void updatePhrase(LoginUser user, Long id, UserQuickPhrase phrase);

    /**
     * 删除常用语（只能删自己的）。
     *
     * @param user 当前登录用户
     * @param id   常用语ID
     */
    void deletePhrase(LoginUser user, Long id);
}
