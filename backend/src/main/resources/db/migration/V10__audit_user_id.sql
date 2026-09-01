-- ============================================================
-- V10__audit_user_id.sql
-- Flyway 迁移脚本：业务表补齐审计人员 ID 字段
--   creator_id / updater_id（配合 V7 已有的 creator / updater 用户名）
--   由 MyBatis Plus MetaObjectHandler 自动填充（取自 JWT 的 userId 声明），
--   供后续按操作人 ID 关联查询（用户名仅展示冗余）
-- 表清单与 V7 保持一致；关系表（sys_user_role / sys_role_menu / sys_role_dept）无审计字段，跳过
-- 规约：迁移脚本只增不改，后续变更请新增 V11__xxx.sql
-- ============================================================

ALTER TABLE sys_user              ADD COLUMN creator_id BIGINT DEFAULT NULL COMMENT '创建人ID' AFTER updater,
                                  ADD COLUMN updater_id BIGINT DEFAULT NULL COMMENT '修改人ID' AFTER creator_id;
ALTER TABLE sys_tenant            ADD COLUMN creator_id BIGINT DEFAULT NULL COMMENT '创建人ID' AFTER updater,
                                  ADD COLUMN updater_id BIGINT DEFAULT NULL COMMENT '修改人ID' AFTER creator_id;
ALTER TABLE sys_dept              ADD COLUMN creator_id BIGINT DEFAULT NULL COMMENT '创建人ID' AFTER updater,
                                  ADD COLUMN updater_id BIGINT DEFAULT NULL COMMENT '修改人ID' AFTER creator_id;
ALTER TABLE sys_role              ADD COLUMN creator_id BIGINT DEFAULT NULL COMMENT '创建人ID' AFTER updater,
                                  ADD COLUMN updater_id BIGINT DEFAULT NULL COMMENT '修改人ID' AFTER creator_id;
ALTER TABLE sys_menu              ADD COLUMN creator_id BIGINT DEFAULT NULL COMMENT '创建人ID' AFTER updater,
                                  ADD COLUMN updater_id BIGINT DEFAULT NULL COMMENT '修改人ID' AFTER creator_id;
ALTER TABLE sys_dict_type         ADD COLUMN creator_id BIGINT DEFAULT NULL COMMENT '创建人ID' AFTER updater,
                                  ADD COLUMN updater_id BIGINT DEFAULT NULL COMMENT '修改人ID' AFTER creator_id;
ALTER TABLE sys_dict_data         ADD COLUMN creator_id BIGINT DEFAULT NULL COMMENT '创建人ID' AFTER updater,
                                  ADD COLUMN updater_id BIGINT DEFAULT NULL COMMENT '修改人ID' AFTER creator_id;
ALTER TABLE agent_config          ADD COLUMN creator_id BIGINT DEFAULT NULL COMMENT '创建人ID' AFTER updater,
                                  ADD COLUMN updater_id BIGINT DEFAULT NULL COMMENT '修改人ID' AFTER creator_id;
ALTER TABLE model_provider_config ADD COLUMN creator_id BIGINT DEFAULT NULL COMMENT '创建人ID' AFTER updater,
                                  ADD COLUMN updater_id BIGINT DEFAULT NULL COMMENT '修改人ID' AFTER creator_id;
ALTER TABLE agent_preset          ADD COLUMN creator_id BIGINT DEFAULT NULL COMMENT '创建人ID' AFTER updater,
                                  ADD COLUMN updater_id BIGINT DEFAULT NULL COMMENT '修改人ID' AFTER creator_id;
ALTER TABLE prompt_template       ADD COLUMN creator_id BIGINT DEFAULT NULL COMMENT '创建人ID' AFTER updater,
                                  ADD COLUMN updater_id BIGINT DEFAULT NULL COMMENT '修改人ID' AFTER creator_id;
ALTER TABLE agent_pipeline        ADD COLUMN creator_id BIGINT DEFAULT NULL COMMENT '创建人ID' AFTER updater,
                                  ADD COLUMN updater_id BIGINT DEFAULT NULL COMMENT '修改人ID' AFTER creator_id;
ALTER TABLE chat_session          ADD COLUMN creator_id BIGINT DEFAULT NULL COMMENT '创建人ID' AFTER updater,
                                  ADD COLUMN updater_id BIGINT DEFAULT NULL COMMENT '修改人ID' AFTER creator_id;
