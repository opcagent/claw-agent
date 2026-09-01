-- ------------------------------------------------------------
-- V40：OCR 识别工具配置项（百度智能云 + 腾讯云）
-- 在 agent_config 表登记 OCR 凭证配置键（PLATFORM 级，默认空值）。
-- 管理员在「系统管理 → 运行参数」页面填写即可全局生效，
-- 也支持 TENANT / USER 级覆盖（三级作用域）。
-- 运行时优先百度智能云，百度未配置或失败时降级到腾讯云。
-- ------------------------------------------------------------

-- 百度智能云 OCR API Key
INSERT INTO agent_config (scope, tenant_id, owner_id, config_key, config_value, remark, create_time, update_time)
SELECT 'PLATFORM', 0, NULL, 'baidu.ocr.api_key', '',
       'OCR API Key - 百度智能云（控制台：https://console.bce.baidu.com/ai/#/ai/ocr/overview/index）',
       NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM agent_config WHERE scope = 'PLATFORM' AND tenant_id = 0 AND owner_id IS NULL AND config_key = 'baidu.ocr.api_key'
);

-- 百度智能云 OCR Secret Key
INSERT INTO agent_config (scope, tenant_id, owner_id, config_key, config_value, remark, create_time, update_time)
SELECT 'PLATFORM', 0, NULL, 'baidu.ocr.secret_key', '',
       'OCR Secret Key - 百度智能云（与 API Key 配对使用）',
       NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM agent_config WHERE scope = 'PLATFORM' AND tenant_id = 0 AND owner_id IS NULL AND config_key = 'baidu.ocr.secret_key'
);

-- 腾讯云 OCR SecretId
INSERT INTO agent_config (scope, tenant_id, owner_id, config_key, config_value, remark, create_time, update_time)
SELECT 'PLATFORM', 0, NULL, 'tencent.ocr.secret_id', '',
       'OCR SecretId - 腾讯云（控制台：https://console.cloud.tencent.com/cam/capi）',
       NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM agent_config WHERE scope = 'PLATFORM' AND tenant_id = 0 AND owner_id IS NULL AND config_key = 'tencent.ocr.secret_id'
);

-- 腾讯云 OCR SecretKey
INSERT INTO agent_config (scope, tenant_id, owner_id, config_key, config_value, remark, create_time, update_time)
SELECT 'PLATFORM', 0, NULL, 'tencent.ocr.secret_key', '',
       'OCR SecretKey - 腾讯云（与 SecretId 配对使用）',
       NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM agent_config WHERE scope = 'PLATFORM' AND tenant_id = 0 AND owner_id IS NULL AND config_key = 'tencent.ocr.secret_key'
);
