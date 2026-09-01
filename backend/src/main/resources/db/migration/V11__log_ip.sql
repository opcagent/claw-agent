-- ============================================================
-- V11__log_ip.sql
-- Flyway 迁移脚本：日志表增加访问者 IP 字段
--   sys_oper_log / sys_login_log 各补 ip 列，
--   由 ClientIpFilter 解析（X-Forwarded-For > X-Real-IP > 对端地址）后落库
-- 规约：迁移脚本只增不改，后续变更请新增 V12__xxx.sql
-- ============================================================

ALTER TABLE sys_oper_log   ADD COLUMN ip VARCHAR(64) DEFAULT NULL COMMENT '访问者IP' AFTER oper_name;
ALTER TABLE sys_login_log  ADD COLUMN ip VARCHAR(64) DEFAULT NULL COMMENT '访问者IP' AFTER msg;
