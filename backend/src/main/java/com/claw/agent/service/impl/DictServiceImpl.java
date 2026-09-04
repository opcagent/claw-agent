package com.claw.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.claw.agent.common.BizException;
import com.claw.agent.common.ResultCode;
import com.claw.agent.config.infra.RedisPubSub;
import com.claw.agent.mapper.DictDataMapper;
import com.claw.agent.mapper.DictTypeMapper;
import com.claw.agent.model.DictData;
import com.claw.agent.model.DictType;
import com.claw.agent.security.LoginUser;
import com.claw.agent.service.DictService;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 字典服务实现。
 * <p>
 * 读取合并规则：平台公共字典（tenant_id=0）+ 本租户字典，
 * 同键值时租户覆盖平台（若依风格扩展点）。
 * 管理端写入均以 (tenantId, ...) 为边界，租户间数据不可见不可改。
 */
@Service
@RequiredArgsConstructor
public class DictServiceImpl extends ServiceImpl<DictDataMapper, DictData> implements DictService {

    /** 平台公共字典租户ID */
    private static final long PLATFORM_TENANT_ID = 0L;

    private final DictTypeMapper dictTypeMapper;
    /** 字典数据缓存（tenantId:dictType → 合并后的字典列表） */
    private final Cache<String, List<DictData>> dictDataCache;
    /** Redis Pub/Sub（可选） */
    private final RedisPubSub redisPubSub;

    @Override
    public List<DictData> listDataByType(LoginUser current, String dictType) {
        // 缓存键：tenantId:dictType（合并结果按租户隔离）
        String cacheKey = current.getTenantId() + ":" + dictType;
        return dictDataCache.get(cacheKey, k -> {
            // 一次查出平台与本租户两份，按值去重合并（租户覆盖平台）
            List<DictData> platform = selectEnabled(PLATFORM_TENANT_ID, dictType);
            List<DictData> tenant = selectEnabled(current.getTenantId(), dictType);
            Set<String> tenantValues = new HashSet<>();
            tenant.forEach(d -> tenantValues.add(d.getDictValue()));
            List<DictData> merged = new ArrayList<>(tenant);
            platform.stream()
                    .filter(d -> !tenantValues.contains(d.getDictValue()))
                    .forEach(merged::add);
            return merged;
        });
    }

    @Override
    public List<DictType> listTypes(Long tenantId) {
        return dictTypeMapper.selectList(new LambdaQueryWrapper<DictType>()
                .eq(DictType::getTenantId, tenantId)
                .orderByAsc(DictType::getDictType));
    }

    @Override
    public List<DictData> listDataForAdmin(Long tenantId, String dictType) {
        return baseMapper.selectList(new LambdaQueryWrapper<DictData>()
                .eq(DictData::getTenantId, tenantId)
                .eq(DictData::getDictType, dictType)
                .orderByAsc(DictData::getDictSort)
                .orderByAsc(DictData::getId));
    }

    @Override
    public void saveType(DictType type) {
        if (!StringUtils.hasText(type.getDictType()) || !StringUtils.hasText(type.getDictName())) {
            throw new BizException(ResultCode.PARAM_ERROR, "字典名称与字典类型不能为空");
        }
        DictType existed = dictTypeMapper.selectOne(new LambdaQueryWrapper<DictType>()
                .eq(DictType::getTenantId, type.getTenantId())
                .eq(DictType::getDictType, type.getDictType())
                .last("LIMIT 1"));
        if (existed == null) {
            dictTypeMapper.insert(type);
        } else {
            // 字典类型编码不可变（数据表按它关联），仅更新名称/状态/备注；
            // 前端编辑时携带原记录 id 则按 id 校验一致性，防止改错记录
            if (type.getId() != null && !type.getId().equals(existed.getId())) {
                throw new BizException(ResultCode.PARAM_ERROR, "字典类型编码已存在，不可重复");
            }
            existed.setDictName(type.getDictName());
            existed.setStatus(type.getStatus());
            existed.setRemark(type.getRemark());
            dictTypeMapper.updateById(existed);
        }
        // 字典类型变更影响该租户所有字典数据缓存
        invalidateDictCache(type.getTenantId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteType(Long tenantId, Long id) {
        // 先查归属再删：带租户条件防越权删平台/他租户字典；级联清理名下数据避免孤儿记录
        DictType type = dictTypeMapper.selectOne(new LambdaQueryWrapper<DictType>()
                .eq(DictType::getId, id)
                .eq(DictType::getTenantId, tenantId)
                .last("LIMIT 1"));
        if (type == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "字典类型不存在或无权操作");
        }
        dictTypeMapper.deleteById(id);
        baseMapper.delete(new LambdaQueryWrapper<DictData>()
                .eq(DictData::getTenantId, tenantId)
                .eq(DictData::getDictType, type.getDictType()));
        // 删除类型级联删除数据，清空该租户相关缓存
        invalidateDictCache(tenantId);
    }

    @Override
    public void saveData(DictData data) {
        if (!StringUtils.hasText(data.getDictType()) || !StringUtils.hasText(data.getDictLabel())
                || !StringUtils.hasText(data.getDictValue())) {
            throw new BizException(ResultCode.PARAM_ERROR, "字典类型、标签与键值不能为空");
        }
        // 字典数据必须挂在已登记的字典类型下，防止随意创建孤儿数据导致前端无处展示
        DictType type = dictTypeMapper.selectOne(new LambdaQueryWrapper<DictType>()
                .eq(DictType::getTenantId, data.getTenantId())
                .eq(DictType::getDictType, data.getDictType())
                .last("LIMIT 1"));
        if (type == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "字典类型不存在：" + data.getDictType());
        }
        if (data.getDictSort() == null) {
            data.setDictSort(0);
        }
        if (data.getId() == null) {
            baseMapper.insert(data);
        } else {
            // 按 id + 租户双条件更新，防越权改他人数据；不存在时提示而非静默新增（避免丢字段语义歧义）
            DictData existed = baseMapper.selectOne(new LambdaQueryWrapper<DictData>()
                    .eq(DictData::getId, data.getId())
                    .eq(DictData::getTenantId, data.getTenantId())
                    .last("LIMIT 1"));
            if (existed == null) {
                throw new BizException(ResultCode.PARAM_ERROR, "字典数据不存在或无权操作");
            }
            baseMapper.updateById(data);
        }
        // 字典数据变更清空该租户缓存
        invalidateDictCache(data.getTenantId());
    }

    @Override
    public void deleteData(Long tenantId, Long id) {
        // 带租户条件删除，防越权；不存在时静默成功（幂等）
        baseMapper.delete(new LambdaQueryWrapper<DictData>()
                .eq(DictData::getId, id)
                .eq(DictData::getTenantId, tenantId));
        // 删除数据清空该租户缓存
        invalidateDictCache(tenantId);
    }

    /** 查询指定租户下启用且按序排列的字典数据 */
    private List<DictData> selectEnabled(Long tenantId, String dictType) {
        return baseMapper.selectList(new LambdaQueryWrapper<DictData>()
                .eq(DictData::getTenantId, tenantId)
                .eq(DictData::getDictType, dictType)
                .eq(DictData::getStatus, 1)
                .orderByAsc(DictData::getDictSort));
    }

    /**
     * 清空指定租户的字典数据缓存（遍历所有可能的 dictType 键）。
     * <p>
     * 由于缓存键是 tenantId:dictType，删除时需要遍历所有可能的 dictType。
     * 简单做法：遍历缓存的 asMap() 找出匹配 tenantId 前缀的键并失效。
     */
    private void invalidateDictCache(Long tenantId) {
        String prefix = tenantId + ":";
        dictDataCache.asMap().keySet().stream()
                .filter(k -> k.startsWith(prefix))
                .forEach(dictDataCache::invalidate);
        // 同时失效平台公共字典缓存（tenant_id=0），因为合并逻辑依赖它
        if (tenantId != PLATFORM_TENANT_ID) {
            String platformPrefix = PLATFORM_TENANT_ID + ":";
            dictDataCache.asMap().keySet().stream()
                    .filter(k -> k.startsWith(platformPrefix))
                    .forEach(dictDataCache::invalidate);
        }
        if (redisPubSub != null) redisPubSub.publishCacheInvalidate("dictDataCache");
    }
}
