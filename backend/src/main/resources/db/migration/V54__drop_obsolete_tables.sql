-- ============================================================
-- V54__drop_obsolete_tables.sql
-- 清理多组织改造后的废弃表
--   1. sys_user_role —— 已被 sys_user_tenant 完全替代
--   2. sys_role_dept —— 数据权限自定义模式从未实现，死表
-- 注意：旧迁移脚本（V4/V7/V10/V13）中对这些表的引用不可修改，
--       Flyway 按版本号顺序执行，V54 在它们之后运行，安全。
-- ============================================================

DROP TABLE IF EXISTS sys_user_role;
DROP TABLE IF EXISTS sys_role_dept;
