package com.claw.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.claw.agent.common.BizException;
import com.claw.agent.common.ResultCode;
import com.claw.agent.mapper.UserQuickPhraseMapper;
import com.claw.agent.model.UserQuickPhrase;
import com.claw.agent.security.LoginUser;
import com.claw.agent.service.QuickPhraseService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 用户常用语服务实现：按 userId 隔离，sort_order 升序排列。
 * <p>
 * 查询时同时返回系统预设指令（user_id='SYSTEM'），系统指令排在最前。
 */
@Service
public class QuickPhraseServiceImpl extends ServiceImpl<UserQuickPhraseMapper, UserQuickPhrase> implements QuickPhraseService {

    /** 系统预设指令的 user_id 标识 */
    private static final String SYSTEM_USER = "SYSTEM";

    @Override
    public List<UserQuickPhrase> listByUser(LoginUser user) {
        return baseMapper.selectList(new LambdaQueryWrapper<UserQuickPhrase>()
                .and(w -> w.eq(UserQuickPhrase::getUserId, user.getUserId())
                        .or()
                        .eq(UserQuickPhrase::getUserId, SYSTEM_USER))
                .orderByAsc(UserQuickPhrase::getSortOrder)
                .orderByDesc(UserQuickPhrase::getCreateTime));
    }

    @Override
    public void addPhrase(LoginUser user, UserQuickPhrase phrase) {
        if (!StringUtils.hasText(phrase.getTitle()) || phrase.getTitle().isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "标题不能为空");
        }
        if (!StringUtils.hasText(phrase.getContent()) || phrase.getContent().isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "内容不能为空");
        }
        phrase.setTenantId(user.getTenantId());
        phrase.setUserId(user.getUserId());
        phrase.setUsername(user.getUsername());
        if (phrase.getSortOrder() == null) {
            phrase.setSortOrder(0);
        }
        baseMapper.insert(phrase);
    }

    @Override
    public void updatePhrase(LoginUser user, Long id, UserQuickPhrase phrase) {
        UserQuickPhrase existing = getAndCheckOwnership(user, id);
        if (StringUtils.hasText(phrase.getTitle())) {
            existing.setTitle(phrase.getTitle());
        }
        if (StringUtils.hasText(phrase.getContent())) {
            existing.setContent(phrase.getContent());
        }
        if (phrase.getSortOrder() != null) {
            existing.setSortOrder(phrase.getSortOrder());
        }
        baseMapper.updateById(existing);
    }

    @Override
    public void deletePhrase(LoginUser user, Long id) {
        getAndCheckOwnership(user, id);
        baseMapper.deleteById(id);
    }

    /**
     * 查询并校验归属权：只能操作自己的常用语。
     */
    private UserQuickPhrase getAndCheckOwnership(LoginUser user, Long id) {
        UserQuickPhrase phrase = baseMapper.selectById(id);
        if (phrase == null || !phrase.getUserId().equals(user.getUserId())) {
            throw new BizException(ResultCode.NOT_FOUND, "常用语不存在或无权操作");
        }
        return phrase;
    }
}
