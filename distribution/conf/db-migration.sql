-- =====================================================
-- SaaS多租户云盘系统 - 数据迁移脚本
-- 版本: v2.0
-- 创建时间: 2026-05-12
-- 说明: 本脚本用于将现有单租户数据迁移到多租户结构
-- 警告: 执行前请先备份数据库!
-- =====================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =====================================================
-- 迁移步骤说明:
-- 1. 创建默认租户(所有现有数据归属此租户)
-- 2. 将现有用户标记为租户管理员
-- 3. 迁移所有业务数据到默认租户
-- 4. 创建默认组织架构
-- 5. 分配默认角色权限
-- =====================================================

-- =====================================================
-- 步骤1: 创建默认租户
-- =====================================================

-- 创建系统默认租户,所有现有数据将归属此租户
INSERT INTO `r_pan_tenant` (
    `tenant_id`, 
    `tenant_code`, 
    `tenant_name`, 
    `contact_name`, 
    `contact_email`, 
    `status`, 
    `max_user_count`, 
    `max_storage_size`, 
    `used_storage_size`, 
    `user_count`, 
    `create_user`, 
    `update_user`,
    `create_time`,
    `update_time`
)
SELECT 
    1 AS tenant_id,
    'default_tenant' AS tenant_code,
    '默认租户' AS tenant_name,
    '系统管理员' AS contact_name,
    'admin@jfeng.com' AS contact_email,
    1 AS status,
    999999 AS max_user_count,
    1099511627776 AS max_storage_size, -- 1TB
    0 AS used_storage_size,
    (SELECT COUNT(*) FROM `r_pan_user`) AS user_count,
    1 AS create_user,
    1 AS update_user,
    NOW() AS create_time,
    NOW() AS update_time
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `r_pan_tenant` WHERE `tenant_id` = 1
);

-- =====================================================
-- 步骤2: 迁移用户数据
-- =====================================================

-- 为所有现有用户设置默认租户ID
UPDATE `r_pan_user` 
SET 
    `tenant_id` = 1,
    `nickname` = `username`,
    `status` = 1,
    `user_type` = CASE 
        WHEN `user_id` = (SELECT MIN(`user_id`) FROM `r_pan_user`) THEN 1 -- 第一个用户设为租户管理员
        ELSE 0 -- 其他用户设为普通用户
    END,
    `del_flag` = 0
WHERE `tenant_id` = 0;

-- =====================================================
-- 步骤3: 迁移文件数据
-- =====================================================

-- 迁移物理文件表
UPDATE `r_pan_file` 
SET `tenant_id` = 1 
WHERE `tenant_id` = 0;

-- 迁移用户文件表
UPDATE `r_pan_user_file` 
SET `tenant_id` = 1 
WHERE `tenant_id` = 0;

-- 迁移分享表
UPDATE `r_pan_share` 
SET `tenant_id` = 1 
WHERE `tenant_id` = 0;

-- 迁移分享文件关联表
UPDATE `r_pan_share_file` 
SET `tenant_id` = 1 
WHERE `tenant_id` = 0;

-- 迁移搜索历史表
UPDATE `r_pan_user_search_history` 
SET `tenant_id` = 1 
WHERE `tenant_id` = 0;

-- 迁移文件分片表
UPDATE `r_pan_file_chunk` 
SET `tenant_id` = 1 
WHERE `tenant_id` = 0;

-- 迁移错误日志表
UPDATE `r_pan_error_log` 
SET `tenant_id` = 1 
WHERE `tenant_id` = 0;

-- =====================================================
-- 步骤4: 计算租户已使用存储空间
-- =====================================================

-- 更新租户已使用存储空间(从r_pan_file表统计)
UPDATE `r_pan_tenant` t
SET t.`used_storage_size` = (
    SELECT COALESCE(SUM(CAST(f.`file_size` AS UNSIGNED)), 0)
    FROM `r_pan_file` f
    WHERE f.`tenant_id` = t.`tenant_id`
)
WHERE t.`tenant_id` = 1;

-- =====================================================
-- 步骤5: 创建默认组织架构
-- =====================================================

-- 创建默认根部门
INSERT INTO `r_pan_department` (
    `dept_id`,
    `tenant_id`,
    `parent_id`,
    `dept_name`,
    `dept_code`,
    `leader_user_id`,
    `status`,
    `del_flag`,
    `create_user`,
    `update_user`,
    `create_time`,
    `update_time`
)
SELECT 
    1 AS dept_id,
    1 AS tenant_id,
    0 AS parent_id,
    '默认部门' AS dept_name,
    'default_dept' AS dept_code,
    (SELECT MIN(`user_id`) FROM `r_pan_user` WHERE `tenant_id` = 1) AS leader_user_id,
    1 AS status,
    0 AS del_flag,
    1 AS create_user,
    1 AS update_user,
    NOW() AS create_time,
    NOW() AS update_time
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `r_pan_department` WHERE `tenant_id` = 1
);

-- 将所有用户关联到默认部门
INSERT INTO `r_pan_user_department` (
    `tenant_id`,
    `user_id`,
    `dept_id`,
    `is_primary`,
    `create_time`
)
SELECT 
    1 AS tenant_id,
    `user_id`,
    1 AS dept_id,
    1 AS is_primary,
    NOW() AS create_time
FROM `r_pan_user`
WHERE `tenant_id` = 1
AND NOT EXISTS (
    SELECT 1 
    FROM `r_pan_user_department` 
    WHERE `user_id` = `r_pan_user`.`user_id`
);

-- =====================================================
-- 步骤6: 分配默认角色
-- =====================================================

-- 为租户管理员分配角色
INSERT INTO `r_pan_user_role` (
    `tenant_id`,
    `user_id`,
    `role_id`,
    `create_time`
)
SELECT 
    1 AS tenant_id,
    `user_id`,
    2 AS role_id, -- 租户管理员角色
    NOW() AS create_time
FROM `r_pan_user`
WHERE `tenant_id` = 1 
AND `user_type` = 1
AND NOT EXISTS (
    SELECT 1 
    FROM `r_pan_user_role` 
    WHERE `user_id` = `r_pan_user`.`user_id`
);

-- 为普通用户分配角色
INSERT INTO `r_pan_user_role` (
    `tenant_id`,
    `user_id`,
    `role_id`,
    `create_time`
)
SELECT 
    1 AS tenant_id,
    `user_id`,
    3 AS role_id, -- 普通用户角色
    NOW() AS create_time
FROM `r_pan_user`
WHERE `tenant_id` = 1 
AND `user_type` = 0
AND NOT EXISTS (
    SELECT 1 
    FROM `r_pan_user_role` 
    WHERE `user_id` = `r_pan_user`.`user_id`
);

-- =====================================================
-- 步骤7: 创建默认订阅记录
-- =====================================================

-- 为默认租户创建永久订阅记录
INSERT INTO `r_pan_tenant_subscription` (
    `subscription_id`,
    `tenant_id`,
    `package_id`,
    `start_time`,
    `end_time`,
    `status`,
    `auto_renew`,
    `order_no`,
    `price`,
    `create_user`,
    `update_user`,
    `create_time`,
    `update_time`
)
SELECT 
    1 AS subscription_id,
    1 AS tenant_id,
    1 AS package_id, -- 免费版套餐
    '2026-05-12 00:00:00' AS start_time,
    '2099-12-31 23:59:59' AS end_time,
    1 AS status,
    0 AS auto_renew,
    'MIGRATION_ORDER_001' AS order_no,
    0.00 AS price,
    1 AS create_user,
    1 AS update_user,
    NOW() AS create_time,
    NOW() AS update_time
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `r_pan_tenant_subscription` WHERE `tenant_id` = 1
);

-- =====================================================
-- 步骤8: 数据验证
-- =====================================================

-- 验证迁移结果(执行后请检查以下输出)

-- 检查租户数据
SELECT '=== 租户信息 ===' AS info;
SELECT tenant_id, tenant_code, tenant_name, status, user_count, max_storage_size, used_storage_size 
FROM r_pan_tenant 
WHERE tenant_id = 1;

-- 检查用户迁移
SELECT '=== 用户迁移统计 ===' AS info;
SELECT 
    tenant_id,
    COUNT(*) AS total_users,
    SUM(CASE WHEN user_type = 1 THEN 1 ELSE 0 END) AS admin_count,
    SUM(CASE WHEN user_type = 0 THEN 1 ELSE 0 END) AS normal_count
FROM r_pan_user
GROUP BY tenant_id;

-- 检查文件迁移
SELECT '=== 文件迁移统计 ===' AS info;
SELECT 
    tenant_id,
    COUNT(*) AS total_files
FROM r_pan_file
GROUP BY tenant_id;

-- 检查分享迁移
SELECT '=== 分享迁移统计 ===' AS info;
SELECT 
    tenant_id,
    COUNT(*) AS total_shares
FROM r_pan_share
GROUP BY tenant_id;

-- 检查部门创建
SELECT '=== 部门信息 ===' AS info;
SELECT dept_id, tenant_id, dept_name, dept_code, leader_user_id
FROM r_pan_department
WHERE tenant_id = 1;

-- 检查角色分配
SELECT '=== 角色分配统计 ===' AS info;
SELECT 
    r.role_name,
    COUNT(ur.id) AS user_count
FROM r_pan_role r
LEFT JOIN r_pan_user_role ur ON r.role_id = ur.role_id
WHERE r.tenant_id = 0
GROUP BY r.role_id, r.role_name;

-- =====================================================
-- 迁移完成提示
-- =====================================================

SELECT '================================================' AS info;
SELECT '数据迁移完成! 请检查以上验证结果是否正确。' AS info;
SELECT '================================================' AS info;

SET FOREIGN_KEY_CHECKS = 1;
