package com.claw.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.claw.agent.common.BizException;
import com.claw.agent.common.ResultCode;
import com.claw.agent.mapper.AgentPresetMapper;
import com.claw.agent.model.AgentPreset;
import com.claw.agent.security.LoginUser;
import com.claw.agent.service.PresetService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 预设 Agent 模板服务实现。
 * <p>
 * 可见性：平台模板全员可见；租户模板本租户可见；用户模板仅本人可见。
 * 维护权限：PLATFORM 仅平台管理员；TENANT 租户管理员；USER 本人。
 */
@Service
public class PresetServiceImpl extends ServiceImpl<AgentPresetMapper, AgentPreset> implements PresetService {

    @Override
    public List<AgentPreset> listVisible(LoginUser current) {
        return baseMapper.selectList(new LambdaQueryWrapper<AgentPreset>()
                .eq(AgentPreset::getEnabled, 1)
                .and(w -> w
                        .eq(AgentPreset::getScope, AgentPreset.SCOPE_PLATFORM)
                        .or(o -> o.eq(AgentPreset::getScope, AgentPreset.SCOPE_TENANT)
                                .eq(AgentPreset::getTenantId, current.getTenantId()))
                        .or(o -> o.eq(AgentPreset::getScope, AgentPreset.SCOPE_USER)
                                .eq(AgentPreset::getTenantId, current.getTenantId())
                                .eq(AgentPreset::getOwnerId, current.getUserId())))
                .orderByAsc(AgentPreset::getOrderNum));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addPreset(LoginUser current, AgentPreset preset) {
        validateRequired(preset);
        checkWritePermission(current, preset.getScope());
        preset.setTenantId(resolveTenantId(current, preset.getScope()));
        preset.setOwnerId(AgentPreset.SCOPE_USER.equals(preset.getScope())
                ? current.getUserId() : null);
        checkCodeUnique(preset.getScope(), preset.getTenantId(), preset.getOwnerId(),
                preset.getAgentCode(), null);
        preset.setEnabled(preset.getEnabled() == null ? 1 : preset.getEnabled());
        if (preset.getOrderNum() == null) {
            preset.setOrderNum(0);
        }
        baseMapper.insert(preset);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePreset(LoginUser current, Long id, AgentPreset preset) {
        AgentPreset existed = selectOwned(current, id);
        existed.setAgentName(preset.getAgentName());
        existed.setIcon(preset.getIcon());
        existed.setDescription(preset.getDescription());
        existed.setSysPrompt(preset.getSysPrompt());
        existed.setOrderNum(preset.getOrderNum());
        existed.setEnabled(preset.getEnabled());
        baseMapper.updateById(existed);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePreset(LoginUser current, Long id) {
        AgentPreset existed = selectOwned(current, id);
        baseMapper.deleteById(existed.getId());
    }

    /** 必填校验：编码 / 名称 / 人格内容缺一不可（无内容则预设无意义） */
    private void validateRequired(AgentPreset preset) {
        if (!StringUtils.hasText(preset.getAgentCode())
                || !StringUtils.hasText(preset.getAgentName())
                || !StringUtils.hasText(preset.getSysPrompt())) {
            throw new BizException(ResultCode.PARAM_ERROR, "编码、名称与人格内容必填");
        }
    }

    /** 编码唯一性：同作用域三元组内重复直接拒绝（库上唯一键 uk_scope_code 兜底，先查后写给友好提示） */
    private void checkCodeUnique(String scope, Long tenantId, String ownerId,
                                 String code, Long excludeId) {
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<AgentPreset>()
                .eq(AgentPreset::getScope, scope)
                .eq(AgentPreset::getTenantId, tenantId == null ? 0L : tenantId)
                .eq(ownerId != null, AgentPreset::getOwnerId, ownerId)
                .isNull(ownerId == null, AgentPreset::getOwnerId)
                .eq(AgentPreset::getAgentCode, code)
                .ne(excludeId != null, AgentPreset::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException(ResultCode.PRESET_CODE_EXISTS, "预设编码已存在：" + code);
        }
    }

    /** 按ID取模板并校验归属（不存在返回 5001） */
    private AgentPreset selectOwned(LoginUser current, Long id) {
        AgentPreset existed = baseMapper.selectById(id);
        if (existed == null) {
            throw new BizException(ResultCode.PRESET_NOT_FOUND);
        }
        checkOwned(current, existed);
        return existed;
    }

    /** 新建时的作用域写权限 */
    private void checkWritePermission(LoginUser user, String scope) {
        if (AgentPreset.SCOPE_PLATFORM.equals(scope) && !user.isAdmin()) {
            throw new BizException(ResultCode.FORBIDDEN, "仅平台管理员可维护平台预设");
        }
        if (AgentPreset.SCOPE_TENANT.equals(scope) && !user.isTenantAdmin()) {
            throw new BizException(ResultCode.FORBIDDEN, "仅租户管理员可维护租户预设");
        }
    }

    /** 修改/删除时的归属校验 */
    private void checkOwned(LoginUser user, AgentPreset preset) {
        if (AgentPreset.SCOPE_PLATFORM.equals(preset.getScope())) {
            if (!user.isAdmin()) {
                throw new BizException(ResultCode.FORBIDDEN, "仅平台管理员可维护平台预设");
            }
        } else if (AgentPreset.SCOPE_TENANT.equals(preset.getScope())) {
            // 角色校验之外必须比对租户，否则租户 A 的管理员可越权改删租户 B 的预设
            if (!user.isTenantAdmin()
                    || (!user.isAdmin() && !user.getTenantId().equals(preset.getTenantId()))) {
                throw new BizException(ResultCode.FORBIDDEN, "仅本租户管理员可维护租户预设");
            }
        } else if (!user.getUserId().equals(preset.getOwnerId())) {
            throw new BizException(ResultCode.FORBIDDEN, "只能维护本人的预设模板");
        }
    }

    /** 作用域对应租户ID（PLATFORM 为 0） */
    private Long resolveTenantId(LoginUser user, String scope) {
        return AgentPreset.SCOPE_PLATFORM.equals(scope) ? 0L : user.getTenantId();
    }

    // ==================== 模板市场 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishPreset(LoginUser current, Long id) {
        AgentPreset existed = selectOwned(current, id);
        // 只有 USER / TENANT 作用域的模板可发布（PLATFORM 已内置无需发布）
        if (AgentPreset.SCOPE_PLATFORM.equals(existed.getScope())) {
            throw new BizException(ResultCode.PARAM_ERROR, "平台内置模板无需发布");
        }
        existed.setPublished(1);
        // 发布名称/描述默认取模板自身名称/描述，用户可后续修改
        if (!StringUtils.hasText(existed.getPublishName())) {
            existed.setPublishName(existed.getAgentName());
        }
        if (!StringUtils.hasText(existed.getPublishDesc())) {
            existed.setPublishDesc(existed.getDescription());
        }
        existed.setAuthorName(current.getUsername());
        baseMapper.updateById(existed);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unpublishPreset(LoginUser current, Long id) {
        AgentPreset existed = selectOwned(current, id);
        existed.setPublished(0);
        baseMapper.updateById(existed);
    }

    @Override
    public List<AgentPreset> listMarketplace() {
        return baseMapper.selectList(new LambdaQueryWrapper<AgentPreset>()
                .eq(AgentPreset::getPublished, 1)
                .eq(AgentPreset::getEnabled, 1)
                .orderByDesc(AgentPreset::getUseCount)
                .orderByDesc(AgentPreset::getCreateTime));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void useFromMarketplace(LoginUser current, Long id) {
        AgentPreset market = baseMapper.selectById(id);
        if (market == null || !Integer.valueOf(1).equals(market.getPublished())) {
            throw new BizException(ResultCode.PRESET_NOT_FOUND, "市场模板不存在或未发布");
        }
        // 复制到当前用户的 USER 作用域
        AgentPreset copy = new AgentPreset();
        copy.setScope(AgentPreset.SCOPE_USER);
        copy.setTenantId(current.getTenantId());
        copy.setOwnerId(current.getUserId());
        // 编码加后缀避免与原模板冲突
        String newCode = "market_" + market.getAgentCode() + "_" + current.getUserId();
        copy.setAgentCode(newCode);
        copy.setAgentName(market.getAgentName());
        copy.setIcon(market.getIcon());
        copy.setDescription(market.getDescription());
        copy.setSysPrompt(market.getSysPrompt());
        copy.setOrderNum(0);
        copy.setEnabled(1);
        copy.setPublished(0);
        baseMapper.insert(copy);
        // 使用次数 +1
        market.setUseCount((market.getUseCount() == null ? 0 : market.getUseCount()) + 1);
        baseMapper.updateById(market);
    }
}
