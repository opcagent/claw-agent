package com.claw.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.claw.agent.common.BizException;
import com.claw.agent.common.ResultCode;
import com.claw.agent.mapper.UserChannelMapper;
import com.claw.agent.model.UserChannel;
import com.claw.agent.security.LoginUser;
import com.claw.agent.service.UserChannelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 用户渠道绑定服务实现。
 * <p>
 * 归属校验：所有写操作（增/删/改）必须验证记录属于当前用户，防止越权；
 * 查询操作按 userId 过滤，确保数据隔离。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserChannelServiceImpl extends ServiceImpl<UserChannelMapper, UserChannel> implements UserChannelService {

    @Override
    public List<UserChannel> listByUser(LoginUser current) {
        return baseMapper.selectList(new LambdaQueryWrapper<UserChannel>()
                .eq(UserChannel::getUserId, current.getUserId())
                .orderByDesc(UserChannel::getCreateTime));
    }

    @Override
    @Transactional
    public void addChannel(LoginUser current, UserChannel channel) {
        // 校验渠道类型
        if (!StringUtils.hasText(channel.getChannelType())) {
            throw new BizException(ResultCode.PARAM_ERROR, "渠道类型不能为空");
        }
        if (!StringUtils.hasText(channel.getChannelUserId())) {
            throw new BizException(ResultCode.PARAM_ERROR, "渠道用户 ID 不能为空");
        }
        // 设置归属用户
        channel.setUserId(current.getUserId());
        // 检查是否已存在相同绑定
        UserChannel existed = baseMapper.selectOne(new LambdaQueryWrapper<UserChannel>()
                .eq(UserChannel::getUserId, current.getUserId())
                .eq(UserChannel::getChannelType, channel.getChannelType())
                .eq(UserChannel::getChannelGroupId, channel.getChannelGroupId())
                .last("LIMIT 1"));
        if (existed != null) {
            throw new BizException(ResultCode.PARAM_ERROR, "该渠道绑定已存在");
        }
        baseMapper.insert(channel);
        log.info("新增渠道绑定：user={}, channel={}, group={}", current.getUserId(), channel.getChannelType(), channel.getChannelGroupId());
    }

    @Override
    @Transactional
    public void updateChannel(LoginUser current, Long id, UserChannel channel) {
        UserChannel existed = getAndCheckOwnership(current, id);
        // 只允许更新非敏感字段（显示名、群组名、token）
        existed.setChannelUsername(channel.getChannelUsername());
        existed.setChannelGroupName(channel.getChannelGroupName());
        existed.setAccessToken(channel.getAccessToken());
        existed.setRefreshToken(channel.getRefreshToken());
        baseMapper.updateById(existed);
        log.info("更新渠道绑定：id={}, user={}", id, current.getUserId());
    }

    @Override
    @Transactional
    public void deleteChannel(LoginUser current, Long id) {
        getAndCheckOwnership(current, id);
        baseMapper.deleteById(id);
        log.info("删除渠道绑定：id={}, user={}", id, current.getUserId());
    }

    @Override
    public UserChannel findByChannelUser(String channelType, String channelUserId) {
        return baseMapper.selectOne(new LambdaQueryWrapper<UserChannel>()
                .eq(UserChannel::getChannelType, channelType)
                .eq(UserChannel::getChannelUserId, channelUserId)
                .eq(UserChannel::getStatus, 1)
                .last("LIMIT 1"));
    }

    @Override
    public List<UserChannel> findGroupMembers(String channelType, String groupId) {
        return baseMapper.selectList(new LambdaQueryWrapper<UserChannel>()
                .eq(UserChannel::getChannelType, channelType)
                .eq(UserChannel::getChannelGroupId, groupId)
                .eq(UserChannel::getStatus, 1)
                .orderByAsc(UserChannel::getGroupRole));
    }

    @Override
    @Transactional
    public void syncGroupMembers(LoginUser current, Long channelId) {
        UserChannel existed = getAndCheckOwnership(current, channelId);
        // TODO: 调用渠道 API 同步群成员（需要各渠道适配器实现）
        log.info("同步群组成员：channelId={}, user={}", channelId, current.getUserId());
    }

    /**
     * 校验渠道绑定记录归属当前用户，防止越权操作。
     *
     * @param current 当前登录用户
     * @param id      绑定记录 ID
     * @return 绑定记录
     * @throws BizException 记录不存在或不属于当前用户
     */
    private UserChannel getAndCheckOwnership(LoginUser current, Long id) {
        UserChannel channel = baseMapper.selectById(id);
        if (channel == null) {
            throw new BizException(ResultCode.NOT_FOUND, "渠道绑定不存在");
        }
        if (!channel.getUserId().equals(current.getUserId())) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作该渠道绑定");
        }
        return channel;
    }
}
