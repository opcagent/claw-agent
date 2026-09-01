package com.claw.agent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.claw.agent.model.DictData;
import com.claw.agent.model.DictType;
import com.claw.agent.security.LoginUser;

import java.util.List;

/**
 * 字典服务：前端下拉/标签渲染的数据源（只读）+ 管理端维护（增删改）。
 * <p>
 * 多租户：平台公共字典 tenant_id=0 仅平台管理员可维护；
 * 租户字典由租户管理员维护，读取时平台 + 本租户合并（租户覆盖平台）。
 */
public interface DictService extends IService<DictData> {

    /** 作用域：平台公共字典（tenant_id=0） */
    String SCOPE_PLATFORM = "PLATFORM";
    /** 作用域：租户字典 */
    String SCOPE_TENANT = "TENANT";

    /**
     * 按字典类型查询启用的字典数据（平台公共 + 本租户合并，同键值租户覆盖平台）。
     *
     * @param current  当前登录用户（取租户做合并）
     * @param dictType 字典类型
     * @return 合并后的字典数据（按显示顺序）
     */
    List<DictData> listDataByType(LoginUser current, String dictType);

    /**
     * 管理端：查询指定作用域下的字典类型列表（含禁用，按字典类型排序）。
     *
     * @param tenantId 作用域对应的租户ID（平台=0）
     * @return 字典类型列表
     */
    List<DictType> listTypes(Long tenantId);

    /**
     * 管理端：查询指定作用域 + 字典类型下的全部字典数据（含禁用，按序）。
     *
     * @param tenantId 作用域对应的租户ID（平台=0）
     * @param dictType 字典类型
     * @return 字典数据列表
     */
    List<DictData> listDataForAdmin(Long tenantId, String dictType);

    /**
     * 管理端：保存字典类型（新增或按 (tenant, dictType) 更新）。
     *
     * @param type 字典类型（tenantId 由调用方按作用域强制赋值）
     */
    void saveType(DictType type);

    /**
     * 管理端：删除字典类型及其名下全部字典数据（级联）。
     *
     * @param tenantId 作用域对应的租户ID（越权防护：仅能删本作用域数据）
     * @param id       字典类型主键
     */
    void deleteType(Long tenantId, Long id);

    /**
     * 管理端：保存字典数据（新增或更新；校验所属字典类型存在）。
     *
     * @param data 字典数据（tenantId 由调用方按作用域强制赋值）
     */
    void saveData(DictData data);

    /**
     * 管理端：删除字典数据。
     *
     * @param tenantId 作用域对应的租户ID（越权防护）
     * @param id       字典数据主键
     */
    void deleteData(Long tenantId, Long id);
}
