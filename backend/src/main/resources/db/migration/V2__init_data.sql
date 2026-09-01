-- ============================================================
-- V2__init_data.sql
-- Flyway 迁移脚本：初始化基础数据
-- 默认管理员账号：admin / admin123（BCrypt 加密，生产环境请登录后立即修改）
-- ============================================================

-- 初始化管理员账号（密码明文 admin123，与若依默认一致）
INSERT INTO sys_user (username, password, nickname, role, status, remark)
SELECT 'admin',
       '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2',
       '管理员',
       'ADMIN',
       1,
       '系统内置管理员'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'admin');

-- 初始化演示普通账号：user / admin123（供多用户隔离测试）
INSERT INTO sys_user (username, password, nickname, role, status, remark)
SELECT 'user',
       '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2',
       '演示用户',
       'USER',
       1,
       '演示普通用户'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'user');
