# 权限规约：平台管理员 vs 租户管理员

> 本文档基于 V55 迁移脚本后的最终实现，描述 admin 与 tenant_admin 的完整权限边界。

## 一、角色定位

| 维度 | 平台管理员 (admin) | 租户管理员 (tenant_admin) |
|------|:---:|:---:|
| 角色键 | `admin` | `tenant_admin` |
| JWT tenantId | `0`（不属于任何组织） | 实际租户 ID |
| JWT permissions | `*:*:*`（全权限通配） | 按角色-菜单聚合的具体权限 |
| `sys_user_tenant` 记录 | 无 | 有 |
| 登录流程 | 直接签发，跳过组织选择 | 多组织时返回 `needSelectTenant` 让前端选择 |
| 核心职责 | 平台运维：管理租户、监管全平台 | 租户运营：管理本租户用户/角色/部门/配置 |

## 二、菜单授权（V55）

### 可见菜单对照

| 菜单 | admin | tenant_admin | common |
|------|:-----:|:------------:|:------:|
| AI 工作台 (M) | ✅ | ✅ | ✅ |
| ├ 智能对话 | ✅ | ✅ | ✅ |
| ├ 定时任务 | ✅ | ✅ | ❌ |
| 平台治理 (M) | ✅ | ✅ | ✅ 仅导航 |
| ├ 成员与账户 | ✅ | ✅ | ❌ |
| ├ 角色与权限 | ✅ | ✅ | ❌ |
| ├ 菜单权限 | ✅ | ✅ | ❌ |
| ├ 组织架构 | ✅ | ✅ | ❌ |
| ├ **租户空间** | ✅ | **❌** | ❌ |
| ├ 审计日志 | ✅ | ✅ 本租户 | ❌ |
| ├ 数据字典 | ✅ | ✅ | ❌ |
| ├ 在线监控 | ✅ | ✅ 本租户 | ❌ |
| ├ 邮箱配置 | ✅ | ✅ | ❌ |
| ├ 渠道管理 | ✅ | ✅ | ❌ |
| ├ Token 统计 | ✅ | ✅ | ✅ |
| 智能体引擎 (M) | ✅ | ✅ | ✅ |
| ├ 模型与能力 | ✅ | ✅ | ✅ |
| 人格预设 | ✅ | ✅ | ✅ |
| 自动化流水线 | ✅ | ✅ | ✅ |
| 模板市场 | ✅ | ✅ | ❌ |
| 首页 | ✅ | ✅ | ❌ |

> tenant_admin 的审计日志/在线监控由后端 Service 层按 `tenantId` 过滤，只看本组织数据。

### 菜单获取机制

- **admin**：`listMyMenus()` 短路返回全部 `status=1` 的 M/C 菜单（admin 无 `sys_user_tenant` 记录，无法走联表查询）
- **tenant_admin / common**：走 `selectMenusByUserIdAndTenantId` 联表查询 `sys_role_menu`

## 三、功能权限

### 3.1 平台独占功能（仅 admin）

| 功能 | 接口 | 控制机制 |
|------|------|----------|
| 租户管理 CRUD | `/api/adminTenant/*` | 类级 `@PreAuthorize("hasRole('ADMIN')")` |
| 创建租户+初始管理员 | `POST /api/adminTenant/withAdmin` | 同上 |
| 设置租户管理员 | `PUT /api/adminTenant/{id}/admin` | 同上 |
| 查看租户用户列表 | `GET /api/adminTenant/{id}/users` | 同上 |
| 菜单增删改 | `POST/PUT/DELETE /api/adminMenu/*` | 方法级 `@PreAuthorize("hasRole('ADMIN')")` |
| PLATFORM 作用域配置 | `scope=PLATFORM` 的所有配置接口 | `checkScopePermission` → `isAdmin()` |

### 3.2 共享功能（admin + tenant_admin）

| 功能 | admin 数据范围 | tenant_admin 数据范围 | 控制机制 |
|------|:---:|:---:|----------|
| 用户管理 | 跨租户全部 | 仅本租户 | 类级 `hasAnyRole('ADMIN','TENANT_ADMIN')` + Service `isAdmin()` 旁路 |
| 角色管理 | 跨租户全部 | 仅本租户 | 同上 |
| 部门管理 | 跨租户全部 | 仅本租户 | 同上 |
| 菜单查看/关联 | 全部 | 全部 | 类级 `hasAnyRole('ADMIN','TENANT_ADMIN')` |
| 审计日志 | 全租户 | 仅本租户 | Service `isAdmin()` 旁路 |
| 在线监控 | 全租户 | 仅本租户 | Service `isAdmin()` 旁路 |
| 数据字典 | 平台+本租户 | 平台+本租户 | 读取合并，写入按 scope |
| 模型与能力配置 | 按 scope | 按 scope | `checkScopePermission` |
| Token 统计 | 本人 | 本人 | 本人（按 userId 隔离） |
| 预设模板 | 平台+本租户+本人 | 平台(只读)+本租户+本人 | Service 归属校验 |
| 流水线 | 平台+本租户+本人 | 平台(只读)+本租户+本人 | Service 归属校验 |

### 3.3 配置作用域权限

ConfigController 和 CapabilityController 共享三级作用域。前端根据角色自动确定 scope，后端按 scope 校验权限：

| 角色 | 前端发送 scope | 后端校验规则 | 可操作范围 |
|------|:---:|:---:|:---:|
| admin | `PLATFORM` | `isAdmin()` | 平台级配置（全局生效） |
| tenant_admin | `TENANT` | `isTenantAdmin()` | 租户级配置（本租户生效，覆盖平台级） |
| common | `USER` | 任何登录用户 | 用户级配置（仅本人生效，覆盖租户/平台级） |

> 配置解析遵循就近覆盖原则：USER > TENANT > PLATFORM。每个角色只能操作自己作用域的配置，避免越权。

### 3.4 前端配置页 Tab 过滤

`/system/config` 页面左侧菜单按角色过滤：

| Tab | admin | tenant_admin |
|-----|:-----:|:------------:|
| 模型提供商 | ✅ | ✅ |
| 运行参数 | ✅ | ✅ |
| **工具集管理** | ✅ | **❌** |
| 搜索引擎 | ✅ | ✅ |
| MCP 服务器 | ✅ | ✅ |
| 技能管理 | ✅ | ✅ |
| **平台配置** | ✅ | **❌** |

> 实现：`CONFIG_MENU` 中 `adminOnly: true` 的项通过 `visibleMenu.filter()` 隐藏。

## 四、数据隔离

### 4.1 查询旁路模式

admin 跨租户查看数据的 Service 层实现：

```java
// 查询类：admin 跳过 tenantId 过滤 → 看全部租户数据
LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
        .orderByAsc(User::getId);
if (!current.isAdmin()) {
    wrapper.eq(User::getTenantId, current.getTenantId());
}
```

适用 Service：`UserServiceImpl`、`RoleServiceImpl`、`DeptServiceImpl`、`OperLogServiceImpl`、`LoginLogServiceImpl`、`MonitorServiceImpl`

### 4.2 操作旁路模式

admin 操作任意租户资源时，跳过租户归属校验：

```java
// 操作类：admin 跳过租户归属检查 → 可操作任意租户资源
private User selectInTenant(LoginUser current, String id) {
    User existed = baseMapper.selectById(id);
    if (existed == null) throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
    if (current.isAdmin()) return existed;  // admin 跳过租户校验
    if (!current.getTenantId().equals(existed.getTenantId()))
        throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
    return existed;
}
```

适用 Service：`UserServiceImpl.selectInTenant`、`RoleServiceImpl.selectInTenant`、`DeptServiceImpl.selectInTenant`

### 4.3 角色分配特殊处理

admin 给用户分配角色时，使用目标用户的 tenantId（而非 admin 的 0）：

```java
Long targetTenantId = current.isAdmin() ? existed.getTenantId() : current.getTenantId();
```

### 4.4 数据隔离汇总

| Service | admin | tenant_admin |
|---------|-------|-------------|
| UserService | 全部租户全部用户 | 仅本租户用户 |
| RoleService | 全部租户全部角色 | 仅本租户角色 |
| DeptService | 全部租户全部部门 | 仅本租户部门 |
| OperLogService | 全部租户操作日志 | 仅本租户操作日志 |
| LoginLogService | 全部租户登录日志 | 仅本租户登录日志 |
| MonitorService | 全部租户在线用户 | 仅本租户在线用户 |
| DictService | 平台+本租户合并 | 平台+本租户合并 |
| ConfigService | 按 scope: 0/本租户/本人 | 按 scope: 本租户/本人 |
| PresetService | 平台+本租户+本人 | 平台(只读)+本租户+本人 |
| PipelineService | 平台+本租户+本人 | 平台(只读)+本租户+本人 |
| ChatService | 按 userId 隔离 | 按 userId 隔离 |
| TokenUsageService | 按 userId + tenantId(=0) | 按 userId + tenantId(实际) |

## 五、按钮权限

### admin 按钮权限

- JWT 持有 `*:*:*` 通配符，`hasPerm()` 始终返回 `true`
- `hasRole('ADMIN')` 注解直接短路绕过所有方法级权限检查
- 无需在 `sys_role_menu` 中逐个授权按钮

### tenant_admin 按钮权限

- 必须通过 `sys_role_menu` 授予具体权限点（如 `system:user:add`）
- V55 已为 tenant_admin 授权全部 M/C/F 菜单（除 205/206/208）
- 按钮权限点（F 类型）包含：

| 模块 | 权限点 |
|------|--------|
| 用户管理 | `system:user:add` / `system:user:edit` / `system:user:remove` / `system:user:resetPwd` / `system:user:grant` |
| 角色管理 | `system:role:add` / `system:role:edit` / `system:role:remove` / `system:role:grant` |
| 部门管理 | `system:dept:add` / `system:dept:edit` / `system:dept:remove` |
| 菜单管理 | `system:menu:add` / `system:menu:edit` / `system:menu:remove` / `system:menu:grant` |
| 字典管理 | `system:dict:add` / `system:dict:edit` / `system:dict:remove` |
| 渠道管理 | `system:channel:add` / `system:channel:edit` / `system:channel:remove` |

## 六、安全约束

| 约束 | 说明 |
|------|------|
| admin 不可被删除 | `UserServiceImpl.deleteUser` 检查目标是否为 admin，禁止删除 |
| admin 不可被降权 | `saveUserRoles` 不允许修改 admin 用户的角色 |
| **内置角色不可删除** | `admin` / `tenant_admin` / `common` 三个角色 `deleteRole` 抛异常拒绝 |
| **租户不可删除** | `deleteTenant` 始终抛异常，仅支持通过 `updateTenant` 将 `status=0` 来停用 |
| **内置角色不可禁用** | `updateRole` 检查内置角色 status=0 时抛异常拒绝 |
| **内置角色键不可创建** | `checkReservedRoleKey` 禁止租户级创建 roleKey=`admin` 的角色 |
| **角色层级隔离** | tenant_admin 看不到 admin 角色（tenant_id 不同）；common 无角色管理菜单 |
| 跨租户操作需旁路 | tenant_admin 操作必须 `tenantId` 匹配，否则返回 404 |
| 菜单管理增删改仅 admin | `MenuController` 方法级 `@PreAuthorize("hasRole('ADMIN')")` |
| 租户管理仅 admin | `TenantController` 类级 `@PreAuthorize("hasRole('ADMIN')")` |
| 密码 BCrypt 加密 | 全局强制，禁止明文存储 |
| JWT secret 环境变量 | 生产环境通过 `CLAW_JWT_SECRET` 覆盖默认值 |
