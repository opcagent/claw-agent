package com.claw.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.claw.agent.common.BizException;
import com.claw.agent.common.ResultCode;
import com.claw.agent.mapper.AgentPipelineMapper;
import com.claw.agent.model.AgentPipeline;
import com.claw.agent.security.LoginUser;
import com.claw.agent.service.PipelineService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 编排流水线服务实现。
 * <p>
 * 可见性：平台流水线全员可见；租户流水线本租户可见；用户流水线仅本人可见。
 * 维护权限：PLATFORM 仅平台管理员；TENANT 租户管理员；USER 本人。
 * 编码唯一性按「作用域 + 租户 + 归属」三元组内校验，与库上唯一键一致。
 */
@Service
public class PipelineServiceImpl extends ServiceImpl<AgentPipelineMapper, AgentPipeline> implements PipelineService {

    @Override
    public List<AgentPipeline> listVisible(LoginUser current) {
        return baseMapper.selectList(visibleWrapper(current)
                .eq(AgentPipeline::getEnabled, 1)
                .orderByAsc(AgentPipeline::getOrderNum));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addPipeline(LoginUser current, AgentPipeline pipeline) {
        validateRequired(pipeline);
        checkWritePermission(current, pipeline.getScope());
        pipeline.setTenantId(resolveTenantId(current, pipeline.getScope()));
        pipeline.setOwnerId(AgentPipeline.SCOPE_USER.equals(pipeline.getScope())
                ? current.getUserId() : null);
        checkCodeUnique(pipeline.getScope(), pipeline.getTenantId(), pipeline.getOwnerId(),
                pipeline.getPipelineCode(), null);
        pipeline.setEnabled(pipeline.getEnabled() == null ? 1 : pipeline.getEnabled());
        if (pipeline.getOrderNum() == null) {
            pipeline.setOrderNum(0);
        }
        baseMapper.insert(pipeline);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePipeline(LoginUser current, Long id, AgentPipeline pipeline) {
        AgentPipeline existed = selectOwned(current, id);
        existed.setPipelineName(pipeline.getPipelineName());
        existed.setDescription(pipeline.getDescription());
        existed.setSteps(pipeline.getSteps());
        existed.setExceptionHandling(pipeline.getExceptionHandling());
        existed.setOrderNum(pipeline.getOrderNum());
        existed.setEnabled(pipeline.getEnabled());
        baseMapper.updateById(existed);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePipeline(LoginUser current, Long id) {
        AgentPipeline existed = selectOwned(current, id);
        baseMapper.deleteById(existed.getId());
    }

    @Override
    public AgentPipeline resolveEnabled(LoginUser current, String pipelineCode) {
        return baseMapper.selectOne(visibleWrapper(current)
                .eq(AgentPipeline::getPipelineCode, pipelineCode)
                .eq(AgentPipeline::getEnabled, 1)
                .last("LIMIT 1"));
    }

    /** 可见性三元组条件：平台全量 + 本租户 + 本人（与预设模板同规则） */
    private LambdaQueryWrapper<AgentPipeline> visibleWrapper(LoginUser current) {
        return new LambdaQueryWrapper<AgentPipeline>()
                .and(w -> w
                        .eq(AgentPipeline::getScope, AgentPipeline.SCOPE_PLATFORM)
                        .or(o -> o.eq(AgentPipeline::getScope, AgentPipeline.SCOPE_TENANT)
                                .eq(AgentPipeline::getTenantId, current.getTenantId()))
                        .or(o -> o.eq(AgentPipeline::getScope, AgentPipeline.SCOPE_USER)
                                .eq(AgentPipeline::getTenantId, current.getTenantId())
                                .eq(AgentPipeline::getOwnerId, current.getUserId())));
    }

    /** 必填校验：编码 / 名称 / 步骤缺一不可（步骤为空则流水线无执行内容） */
    private void validateRequired(AgentPipeline pipeline) {
        if (!StringUtils.hasText(pipeline.getPipelineCode())
                || !StringUtils.hasText(pipeline.getPipelineName())
                || !StringUtils.hasText(pipeline.getSteps())) {
            throw new BizException(ResultCode.PARAM_ERROR, "编码、名称与执行步骤必填");
        }
    }

    /** 编码唯一性：同作用域三元组内重复直接拒绝（库上唯一键兜底，先查后写给出友好提示） */
    private void checkCodeUnique(String scope, Long tenantId, String ownerId,
                                 String code, Long excludeId) {
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<AgentPipeline>()
                .eq(AgentPipeline::getScope, scope)
                .eq(AgentPipeline::getTenantId, tenantId == null ? 0L : tenantId)
                .eq(ownerId != null, AgentPipeline::getOwnerId, ownerId)
                .isNull(ownerId == null, AgentPipeline::getOwnerId)
                .eq(AgentPipeline::getPipelineCode, code)
                .ne(excludeId != null, AgentPipeline::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException(ResultCode.PIPELINE_CODE_EXISTS, "流水线编码已存在：" + code);
        }
    }

    /** 按ID取流水线并校验归属（不存在返回 404） */
    private AgentPipeline selectOwned(LoginUser current, Long id) {
        AgentPipeline existed = baseMapper.selectById(id);
        if (existed == null) {
            throw new BizException(ResultCode.PIPELINE_NOT_FOUND);
        }
        checkOwned(current, existed);
        return existed;
    }

    /** 新建时的作用域写权限 */
    private void checkWritePermission(LoginUser user, String scope) {
        if (AgentPipeline.SCOPE_PLATFORM.equals(scope) && !user.isAdmin()) {
            throw new BizException(ResultCode.FORBIDDEN, "仅平台管理员可维护平台流水线");
        }
        if (AgentPipeline.SCOPE_TENANT.equals(scope) && !user.isTenantAdmin()) {
            throw new BizException(ResultCode.FORBIDDEN, "仅租户管理员可维护租户流水线");
        }
    }

    /** 修改/删除时的归属校验（租户级必须比对租户，防跨租户越权） */
    private void checkOwned(LoginUser user, AgentPipeline pipeline) {
        if (AgentPipeline.SCOPE_PLATFORM.equals(pipeline.getScope())) {
            if (!user.isAdmin()) {
                throw new BizException(ResultCode.FORBIDDEN, "仅平台管理员可维护平台流水线");
            }
        } else if (AgentPipeline.SCOPE_TENANT.equals(pipeline.getScope())) {
            if (!user.isTenantAdmin()
                    || (!user.isAdmin() && !user.getTenantId().equals(pipeline.getTenantId()))) {
                throw new BizException(ResultCode.FORBIDDEN, "仅本租户管理员可维护租户流水线");
            }
        } else if (!user.getUserId().equals(pipeline.getOwnerId())) {
            throw new BizException(ResultCode.FORBIDDEN, "只能维护本人的流水线");
        }
    }

    /** 作用域对应租户ID（PLATFORM 为 0） */
    private Long resolveTenantId(LoginUser user, String scope) {
        return AgentPipeline.SCOPE_PLATFORM.equals(scope) ? 0L : user.getTenantId();
    }
}
