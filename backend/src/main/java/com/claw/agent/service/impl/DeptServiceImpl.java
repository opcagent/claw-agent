package com.claw.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.claw.agent.common.BizException;
import com.claw.agent.common.ResultCode;
import com.claw.agent.mapper.DeptMapper;
import com.claw.agent.mapper.UserMapper;
import com.claw.agent.model.Dept;
import com.claw.agent.model.User;
import com.claw.agent.security.LoginUser;
import com.claw.agent.service.DeptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/**
 * 部门管理服务实现。
 * <p>
 * ancestors 存父链（如 0,1,5）供「本部门及以下」数据权限前缀匹配，
 * 调整父级时必须同步刷新自身与全部子孙的 ancestors。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeptServiceImpl extends ServiceImpl<DeptMapper, Dept> implements DeptService {

    private final UserMapper userMapper;

    @Override
    public List<Dept> listDepts(LoginUser current) {
        LambdaQueryWrapper<Dept> wrapper = new LambdaQueryWrapper<Dept>()
                .orderByAsc(Dept::getOrderNum);
        // 平台管理员跨租户查看全部部门，租户管理员只看本租户
        if (!current.isAdmin()) {
            wrapper.eq(Dept::getTenantId, current.getTenantId());
        }
        return baseMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addDept(LoginUser current, Dept dept) {
        Dept parent = resolveParent(current, dept.getParentId());
        dept.setTenantId(current.getTenantId());
        dept.setAncestors(buildAncestors(parent, dept.getParentId()));
        dept.setStatus(dept.getStatus() == null ? 1 : dept.getStatus());
        baseMapper.insert(dept);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDept(LoginUser current, Long id, Dept dept) {
        Dept existed = selectInTenant(current, id);
        // 请求未带 parentId 时保留原父部门，避免部门被静默提升为根节点打乱组织树；
        // status 为 NOT NULL 列，同样保留原值防空值穿透报 500
        Long parentId = dept.getParentId() != null ? dept.getParentId() : existed.getParentId();
        if (parentId.equals(id)) {
            throw new BizException(ResultCode.PARAM_ERROR, "父部门不能是自身");
        }
        Dept newParent = resolveParent(current, parentId);
        // 防环：新父部门不能是本人的子孙，否则 ancestors 父链形成回路，数据权限前缀匹配全乱
        if (newParent != null && ancestorContains(newParent.getAncestors(), id)) {
            throw new BizException(ResultCode.PARAM_ERROR, "父部门不能是本部门的子孙部门");
        }
        String newAncestors = buildAncestors(newParent, parentId);
        // 父链变化时批量纠正子孙部门（数据权限依赖 ancestors 前缀，必须保持一致）
        if (!newAncestors.equals(existed.getAncestors())) {
            String oldPrefix = existed.getAncestors() + "," + existed.getId();
            List<Dept> children = baseMapper.selectList(new LambdaQueryWrapper<Dept>()
                    .eq(Dept::getTenantId, current.getTenantId())
                    .likeRight(Dept::getAncestors, oldPrefix));
            for (Dept child : children) {
                child.setAncestors(newAncestors + "," + existed.getId()
                        + child.getAncestors().substring(oldPrefix.length()));
                baseMapper.updateById(child);
            }
        }
        existed.setParentId(parentId);
        existed.setAncestors(newAncestors);
        existed.setDeptName(dept.getDeptName());
        existed.setOrderNum(dept.getOrderNum());
        existed.setLeader(dept.getLeader());
        if (dept.getStatus() != null) {
            existed.setStatus(dept.getStatus());
        }
        baseMapper.updateById(existed);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDept(LoginUser current, Long id) {
        Dept existed = selectInTenant(current, id);
        Long childCount = baseMapper.selectCount(new LambdaQueryWrapper<Dept>()
                .eq(Dept::getTenantId, current.getTenantId())
                .eq(Dept::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "存在子部门，禁止删除");
        }
        Long userCount = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getDeptId, id));
        if (userCount != null && userCount > 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "部门下仍有用户，禁止删除");
        }
        baseMapper.deleteById(id);
    }

    // ------------------------------------------------------------
    // 内部辅助
    // ------------------------------------------------------------

    /** 查询父部门（父级必须在本租户内；根部门 parentId=0 时返回 null） */
    private Dept resolveParent(LoginUser current, Long parentId) {
        if (parentId == null || parentId == 0L) {
            return null;
        }
        return selectInTenant(current, parentId);
    }

    /** 组装父链：根为 "0"，否则为 父父链 + "," + 父ID */
    private String buildAncestors(Dept parent, Long parentId) {
        if (parent == null) {
            return "0";
        }
        return parent.getAncestors() + "," + parentId;
    }

    /** ancestors 父链中是否包含指定部门ID（移动部门时防环用） */
    private boolean ancestorContains(String ancestors, Long deptId) {
        if (ancestors == null) {
            return false;
        }
        String target = String.valueOf(deptId);
        for (String seg : ancestors.split(",")) {
            if (seg.equals(target)) {
                return true;
            }
        }
        return false;
    }

    /** 租户内部门查询（越租户访问返回 404，防信息泄漏）；平台管理员可操作任意租户部门 */
    private Dept selectInTenant(LoginUser current, Long id) {
        Dept existed = baseMapper.selectById(id);
        if (existed == null) {
            throw new BizException(ResultCode.NOT_FOUND, "部门不存在");
        }
        if (current.isAdmin()) {
            return existed;
        }
        if (!current.getTenantId().equals(existed.getTenantId())) {
            throw new BizException(ResultCode.NOT_FOUND, "部门不存在");
        }
        return existed;
    }
}
