SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =====================================================
-- SaaS多租户云盘系统 - 数据库设计
-- 版本: v2.0
-- 创建时间: 2026-05-12
-- 说明: 本文件包含多租户改造的所有新增表结构
-- =====================================================

-- =====================================================
-- 1. 租户管理模块
-- =====================================================

-- ----------------------------
-- Table structure for r_pan_tenant (租户信息表)
-- ----------------------------
DROP TABLE IF EXISTS `r_pan_tenant`;
CREATE TABLE `r_pan_tenant` (
    `tenant_id`             bigint(20) NOT NULL COMMENT '租户ID',
    `tenant_code`           varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '租户编码(唯一标识)',
    `tenant_name`           varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '租户名称',
    `tenant_domain`         varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '租户专属域名(可选)',
    `contact_name`          varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '联系人姓名',
    `contact_phone`         varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '联系人电话',
    `contact_email`         varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '联系人邮箱',
    `logo_url`              varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '租户Logo地址',
    `theme_color`           varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '#1890ff' COMMENT '主题色',
    `status`                tinyint(1) NOT NULL DEFAULT 0 COMMENT '租户状态(0 待审核 1 正常 2 冻结 3 已过期)',
    `expire_time`           datetime DEFAULT NULL COMMENT '租户过期时间(NULL表示永久)',
    `max_user_count`        int(11) NOT NULL DEFAULT 10 COMMENT '最大用户数限制',
    `max_storage_size`      bigint(20) NOT NULL DEFAULT 10737418240 COMMENT '最大存储空间(字节,默认10GB)',
    `used_storage_size`     bigint(20) NOT NULL DEFAULT 0 COMMENT '已使用存储空间(字节)',
    `user_count`            int(11) NOT NULL DEFAULT 0 COMMENT '当前用户数',
    `features`              varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '功能特性(JSON格式)',
    `remark`                varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '备注',
    `create_user`           bigint(20) NOT NULL COMMENT '创建人ID',
    `create_time`           datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user`           bigint(20) NOT NULL COMMENT '更新人ID',
    `update_time`           datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`tenant_id`) USING BTREE,
    UNIQUE INDEX `uk_tenant_code` (`tenant_code`) USING BTREE COMMENT '租户编码唯一索引',
    UNIQUE INDEX `uk_tenant_domain` (`tenant_domain`) USING BTREE COMMENT '租户域名唯一索引',
    INDEX `idx_status` (`status`) USING BTREE COMMENT '状态索引',
    INDEX `idx_expire_time` (`expire_time`) USING BTREE COMMENT '过期时间索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=DYNAMIC COMMENT='租户信息表';

-- ----------------------------
-- Table structure for r_pan_tenant_package (租户套餐表)
-- ----------------------------
DROP TABLE IF EXISTS `r_pan_tenant_package`;
CREATE TABLE `r_pan_tenant_package` (
    `package_id`            bigint(20) NOT NULL COMMENT '套餐ID',
    `package_name`          varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '套餐名称',
    `package_code`          varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '套餐编码',
    `package_desc`          varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '套餐描述',
    `price`                 decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '套餐价格(元/月)',
    `max_user_count`        int(11) NOT NULL DEFAULT 10 COMMENT '最大用户数',
    `max_storage_size`      bigint(20) NOT NULL DEFAULT 10737418240 COMMENT '最大存储空间(字节)',
    `max_file_size`         bigint(20) NOT NULL DEFAULT 104857600 COMMENT '单文件最大大小(字节,默认100MB)',
    `max_share_count`       int(11) NOT NULL DEFAULT 100 COMMENT '最大分享数',
    `features`              varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '功能特性(JSON格式)',
    `status`                tinyint(1) NOT NULL DEFAULT 1 COMMENT '状态(0 禁用 1 启用)',
    `sort_order`            int(11) NOT NULL DEFAULT 0 COMMENT '排序',
    `create_user`           bigint(20) NOT NULL COMMENT '创建人ID',
    `create_time`           datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user`           bigint(20) NOT NULL COMMENT '更新人ID',
    `update_time`           datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`package_id`) USING BTREE,
    UNIQUE INDEX `uk_package_code` (`package_code`) USING BTREE COMMENT '套餐编码唯一索引',
    INDEX `idx_status` (`status`) USING BTREE COMMENT '状态索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=DYNAMIC COMMENT='租户套餐表';

-- ----------------------------
-- Table structure for r_pan_tenant_subscription (租户订阅表)
-- ----------------------------
DROP TABLE IF EXISTS `r_pan_tenant_subscription`;
CREATE TABLE `r_pan_tenant_subscription` (
    `subscription_id`       bigint(20) NOT NULL COMMENT '订阅ID',
    `tenant_id`             bigint(20) NOT NULL COMMENT '租户ID',
    `package_id`            bigint(20) NOT NULL COMMENT '套餐ID',
    `start_time`            datetime NOT NULL COMMENT '订阅开始时间',
    `end_time`              datetime NOT NULL COMMENT '订阅结束时间',
    `status`                tinyint(1) NOT NULL DEFAULT 0 COMMENT '订阅状态(0 未生效 1 生效中 2 已过期 3 已取消)',
    `auto_renew`            tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否自动续费(0 否 1 是)',
    `order_no`              varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '订单号',
    `price`                 decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '实际支付价格',
    `remark`                varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '备注',
    `create_user`           bigint(20) NOT NULL COMMENT '创建人ID',
    `create_time`           datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user`           bigint(20) NOT NULL COMMENT '更新人ID',
    `update_time`           datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`subscription_id`) USING BTREE,
    INDEX `idx_tenant_id` (`tenant_id`) USING BTREE COMMENT '租户ID索引',
    INDEX `idx_package_id` (`package_id`) USING BTREE COMMENT '套餐ID索引',
    INDEX `idx_status` (`status`) USING BTREE COMMENT '状态索引',
    INDEX `idx_end_time` (`end_time`) USING BTREE COMMENT '结束时间索引',
    UNIQUE INDEX `uk_order_no` (`order_no`) USING BTREE COMMENT '订单号唯一索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=DYNAMIC COMMENT='租户订阅表';

-- =====================================================
-- 2. 组织架构模块
-- =====================================================

-- ----------------------------
-- Table structure for r_pan_department (部门表)
-- ----------------------------
DROP TABLE IF EXISTS `r_pan_department`;
CREATE TABLE `r_pan_department` (
    `dept_id`               bigint(20) NOT NULL COMMENT '部门ID',
    `tenant_id`             bigint(20) NOT NULL COMMENT '租户ID',
    `parent_id`             bigint(20) NOT NULL DEFAULT 0 COMMENT '父部门ID(顶级部门为0)',
    `dept_name`             varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '部门名称',
    `dept_code`             varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '部门编码',
    `leader_user_id`        bigint(20) DEFAULT NULL COMMENT '部门负责人ID',
    `phone`                 varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '联系电话',
    `email`                 varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '邮箱',
    `sort_order`            int(11) NOT NULL DEFAULT 0 COMMENT '排序',
    `status`                tinyint(1) NOT NULL DEFAULT 1 COMMENT '状态(0 禁用 1 启用)',
    `del_flag`              tinyint(1) NOT NULL DEFAULT 0 COMMENT '删除标识(0 否 1 是)',
    `create_user`           bigint(20) NOT NULL COMMENT '创建人ID',
    `create_time`           datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user`           bigint(20) NOT NULL COMMENT '更新人ID',
    `update_time`           datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`dept_id`) USING BTREE,
    INDEX `idx_tenant_id` (`tenant_id`) USING BTREE COMMENT '租户ID索引',
    INDEX `idx_parent_id` (`parent_id`) USING BTREE COMMENT '父部门ID索引',
    UNIQUE INDEX `uk_tenant_dept_code` (`tenant_id`, `dept_code`) USING BTREE COMMENT '租户+部门编码唯一索引',
    INDEX `idx_status` (`status`) USING BTREE COMMENT '状态索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=DYNAMIC COMMENT='部门表';

-- ----------------------------
-- Table structure for r_pan_user_department (用户部门关联表)
-- ----------------------------
DROP TABLE IF EXISTS `r_pan_user_department`;
CREATE TABLE `r_pan_user_department` (
    `id`                    bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`             bigint(20) NOT NULL COMMENT '租户ID',
    `user_id`               bigint(20) NOT NULL COMMENT '用户ID',
    `dept_id`               bigint(20) NOT NULL COMMENT '部门ID',
    `is_primary`            tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否主部门(0 否 1 是)',
    `position`              varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '职位',
    `create_time`           datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_tenant_id` (`tenant_id`) USING BTREE COMMENT '租户ID索引',
    INDEX `idx_user_id` (`user_id`) USING BTREE COMMENT '用户ID索引',
    INDEX `idx_dept_id` (`dept_id`) USING BTREE COMMENT '部门ID索引',
    UNIQUE INDEX `uk_user_dept` (`tenant_id`, `user_id`, `dept_id`) USING BTREE COMMENT '用户部门唯一索引'
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=DYNAMIC COMMENT='用户部门关联表';

-- =====================================================
-- 3. RBAC权限模块
-- =====================================================

-- ----------------------------
-- Table structure for r_pan_role (角色表)
-- ----------------------------
DROP TABLE IF EXISTS `r_pan_role`;
CREATE TABLE `r_pan_role` (
    `role_id`               bigint(20) NOT NULL COMMENT '角色ID',
    `tenant_id`             bigint(20) NOT NULL COMMENT '租户ID',
    `role_name`             varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '角色名称',
    `role_code`             varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '角色编码',
    `role_type`             tinyint(1) NOT NULL DEFAULT 0 COMMENT '角色类型(0 自定义 1 系统内置)',
    `role_desc`             varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '角色描述',
    `data_scope`            tinyint(1) NOT NULL DEFAULT 0 COMMENT '数据权限范围(0 仅本人 1 本部门 2 本部门及下级 3 全租户)',
    `status`                tinyint(1) NOT NULL DEFAULT 1 COMMENT '状态(0 禁用 1 启用)',
    `sort_order`            int(11) NOT NULL DEFAULT 0 COMMENT '排序',
    `del_flag`              tinyint(1) NOT NULL DEFAULT 0 COMMENT '删除标识(0 否 1 是)',
    `create_user`           bigint(20) NOT NULL COMMENT '创建人ID',
    `create_time`           datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user`           bigint(20) NOT NULL COMMENT '更新人ID',
    `update_time`           datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`role_id`) USING BTREE,
    INDEX `idx_tenant_id` (`tenant_id`) USING BTREE COMMENT '租户ID索引',
    UNIQUE INDEX `uk_tenant_role_code` (`tenant_id`, `role_code`) USING BTREE COMMENT '租户+角色编码唯一索引',
    INDEX `idx_status` (`status`) USING BTREE COMMENT '状态索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=DYNAMIC COMMENT='角色表';

-- ----------------------------
-- Table structure for r_pan_permission (权限表)
-- ----------------------------
DROP TABLE IF EXISTS `r_pan_permission`;
CREATE TABLE `r_pan_permission` (
    `permission_id`         bigint(20) NOT NULL COMMENT '权限ID',
    `parent_id`             bigint(20) NOT NULL DEFAULT 0 COMMENT '父权限ID(顶级权限为0)',
    `permission_name`       varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '权限名称',
    `permission_code`       varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '权限编码(如:file:upload)',
    `permission_type`       tinyint(1) NOT NULL DEFAULT 0 COMMENT '权限类型(0 菜单 1 按钮 2 接口)',
    `path`                  varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '路由路径',
    `component`             varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '组件路径',
    `icon`                  varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '图标',
    `sort_order`            int(11) NOT NULL DEFAULT 0 COMMENT '排序',
    `status`                tinyint(1) NOT NULL DEFAULT 1 COMMENT '状态(0 禁用 1 启用)',
    `create_time`           datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`           datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`permission_id`) USING BTREE,
    UNIQUE INDEX `uk_permission_code` (`permission_code`) USING BTREE COMMENT '权限编码唯一索引',
    INDEX `idx_parent_id` (`parent_id`) USING BTREE COMMENT '父权限ID索引',
    INDEX `idx_status` (`status`) USING BTREE COMMENT '状态索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=DYNAMIC COMMENT='权限表';

-- ----------------------------
-- Table structure for r_pan_role_permission (角色权限关联表)
-- ----------------------------
DROP TABLE IF EXISTS `r_pan_role_permission`;
CREATE TABLE `r_pan_role_permission` (
    `id`                    bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
    `role_id`               bigint(20) NOT NULL COMMENT '角色ID',
    `permission_id`         bigint(20) NOT NULL COMMENT '权限ID',
    `create_time`           datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_role_id` (`role_id`) USING BTREE COMMENT '角色ID索引',
    INDEX `idx_permission_id` (`permission_id`) USING BTREE COMMENT '权限ID索引',
    UNIQUE INDEX `uk_role_permission` (`role_id`, `permission_id`) USING BTREE COMMENT '角色权限唯一索引'
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=DYNAMIC COMMENT='角色权限关联表';

-- ----------------------------
-- Table structure for r_pan_user_role (用户角色关联表)
-- ----------------------------
DROP TABLE IF EXISTS `r_pan_user_role`;
CREATE TABLE `r_pan_user_role` (
    `id`                    bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`             bigint(20) NOT NULL COMMENT '租户ID',
    `user_id`               bigint(20) NOT NULL COMMENT '用户ID',
    `role_id`               bigint(20) NOT NULL COMMENT '角色ID',
    `create_time`           datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_tenant_id` (`tenant_id`) USING BTREE COMMENT '租户ID索引',
    INDEX `idx_user_id` (`user_id`) USING BTREE COMMENT '用户ID索引',
    INDEX `idx_role_id` (`role_id`) USING BTREE COMMENT '角色ID索引',
    UNIQUE INDEX `uk_user_role` (`tenant_id`, `user_id`, `role_id`) USING BTREE COMMENT '用户角色唯一索引'
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=DYNAMIC COMMENT='用户角色关联表';

-- =====================================================
-- 4. 现有表结构改造 - 添加tenant_id字段
-- =====================================================

-- ----------------------------
-- 改造 r_pan_user 表 - 添加租户相关字段
-- ----------------------------
ALTER TABLE `r_pan_user`
    ADD COLUMN `tenant_id` bigint(20) NOT NULL DEFAULT 0 COMMENT '租户ID(0表示系统管理员)' AFTER `user_id`,
    ADD COLUMN `nickname` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '昵称' AFTER `username`,
    ADD COLUMN `avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '头像地址' AFTER `nickname`,
    ADD COLUMN `phone` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '手机号' AFTER `avatar`,
    ADD COLUMN `email` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '邮箱' AFTER `phone`,
    ADD COLUMN `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '状态(0 禁用 1 启用)' AFTER `answer`,
    ADD COLUMN `user_type` tinyint(1) NOT NULL DEFAULT 0 COMMENT '用户类型(0 普通用户 1 租户管理员 2 系统管理员)' AFTER `status`,
    ADD COLUMN `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间' AFTER `update_time`,
    ADD COLUMN `last_login_ip` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后登录IP' AFTER `last_login_time`,
    ADD COLUMN `del_flag` tinyint(1) NOT NULL DEFAULT 0 COMMENT '删除标识(0 否 1 是)' AFTER `user_type`,
    ADD INDEX `idx_tenant_id` (`tenant_id`) USING BTREE COMMENT '租户ID索引',
    ADD INDEX `idx_status` (`status`) USING BTREE COMMENT '状态索引',
    DROP INDEX `uk_username`,
    ADD UNIQUE INDEX `uk_tenant_username` (`tenant_id`, `username`) USING BTREE COMMENT '租户+用户名唯一索引';

-- ----------------------------
-- 改造 r_pan_file 表 - 添加租户ID字段
-- ----------------------------
ALTER TABLE `r_pan_file`
    ADD COLUMN `tenant_id` bigint(20) NOT NULL DEFAULT 0 COMMENT '租户ID' AFTER `file_id`,
    ADD INDEX `idx_tenant_id` (`tenant_id`) USING BTREE COMMENT '租户ID索引',
    ADD INDEX `idx_tenant_create_user` (`tenant_id`, `create_user`) USING BTREE COMMENT '租户+创建人索引';

-- ----------------------------
-- 改造 r_pan_user_file 表 - 添加租户ID字段
-- ----------------------------
ALTER TABLE `r_pan_user_file`
    ADD COLUMN `tenant_id` bigint(20) NOT NULL DEFAULT 0 COMMENT '租户ID' AFTER `file_id`,
    ADD COLUMN `share_scope` tinyint(1) NOT NULL DEFAULT 0 COMMENT '共享范围(0 私有 1 部门共享 2 租户共享)' AFTER `del_flag`,
    ADD INDEX `idx_tenant_id` (`tenant_id`) USING BTREE COMMENT '租户ID索引',
    DROP INDEX `index_file_list`,
    ADD INDEX `idx_file_list` (`tenant_id`, `user_id`, `del_flag`, `parent_id`, `file_type`, `file_id`, `filename`, `folder_flag`, `file_size_desc`, `create_time`, `update_time`) USING BTREE COMMENT '查询文件列表索引';

-- ----------------------------
-- 改造 r_pan_share 表 - 添加租户ID字段
-- ----------------------------
ALTER TABLE `r_pan_share`
    ADD COLUMN `tenant_id` bigint(20) NOT NULL DEFAULT 0 COMMENT '租户ID' AFTER `share_id`,
    ADD COLUMN `share_scope` tinyint(1) NOT NULL DEFAULT 0 COMMENT '分享范围(0 公开链接 1 租户内 2 部门内)' AFTER `share_status`,
    ADD INDEX `idx_tenant_id` (`tenant_id`) USING BTREE COMMENT '租户ID索引',
    DROP INDEX `uk_create_user_time`,
    ADD UNIQUE INDEX `uk_tenant_create_user_time` (`tenant_id`, `create_user`, `create_time`) USING BTREE COMMENT '租户+创建人+创建时间唯一索引';

-- ----------------------------
-- 改造 r_pan_share_file 表 - 添加租户ID字段
-- ----------------------------
ALTER TABLE `r_pan_share_file`
    ADD COLUMN `tenant_id` bigint(20) NOT NULL DEFAULT 0 COMMENT '租户ID' AFTER `id`,
    ADD INDEX `idx_tenant_id` (`tenant_id`) USING BTREE COMMENT '租户ID索引';

-- ----------------------------
-- 改造 r_pan_user_search_history 表 - 添加租户ID字段
-- ----------------------------
ALTER TABLE `r_pan_user_search_history`
    ADD COLUMN `tenant_id` bigint(20) NOT NULL DEFAULT 0 COMMENT '租户ID' AFTER `id`,
    ADD INDEX `idx_tenant_id` (`tenant_id`) USING BTREE COMMENT '租户ID索引',
    DROP INDEX `uk_user_id_search_content_update_time`,
    DROP INDEX `uk_user_id_search_content`,
    ADD UNIQUE INDEX `uk_tenant_user_search` (`tenant_id`, `user_id`, `search_content`) USING BTREE COMMENT '租户+用户+搜索内容唯一索引';

-- ----------------------------
-- 改造 r_pan_file_chunk 表 - 添加租户ID字段
-- ----------------------------
ALTER TABLE `r_pan_file_chunk`
    ADD COLUMN `tenant_id` bigint(20) NOT NULL DEFAULT 0 COMMENT '租户ID' AFTER `id`,
    ADD INDEX `idx_tenant_id` (`tenant_id`) USING BTREE COMMENT '租户ID索引',
    DROP INDEX `uk_identifier_chunk_number_create_user`,
    ADD UNIQUE INDEX `uk_tenant_identifier_chunk` (`tenant_id`, `identifier`, `chunk_number`, `create_user`) USING BTREE COMMENT '租户+文件标识+分片编号+用户唯一索引';

-- ----------------------------
-- 改造 r_pan_error_log 表 - 添加租户ID字段
-- ----------------------------
ALTER TABLE `r_pan_error_log`
    ADD COLUMN `tenant_id` bigint(20) NOT NULL DEFAULT 0 COMMENT '租户ID' AFTER `id`,
    ADD INDEX `idx_tenant_id` (`tenant_id`) USING BTREE COMMENT '租户ID索引';

-- =====================================================
-- 5. 初始化数据
-- =====================================================

-- ----------------------------
-- 初始化系统管理员租户
-- ----------------------------
INSERT INTO `r_pan_tenant` (`tenant_id`, `tenant_code`, `tenant_name`, `contact_name`, `contact_email`, `status`, `max_user_count`, `max_storage_size`, `used_storage_size`, `user_count`, `create_user`, `update_user`)
VALUES (1, 'system', '系统默认租户', '系统管理员', 'admin@jfeng.com', 1, 999999, 1099511627776, 0, 0, 1, 1);

-- ----------------------------
-- 初始化默认套餐
-- ----------------------------
INSERT INTO `r_pan_tenant_package` (`package_id`, `package_name`, `package_code`, `package_desc`, `price`, `max_user_count`, `max_storage_size`, `max_file_size`, `max_share_count`, `features`, `status`, `sort_order`, `create_user`, `update_user`)
VALUES 
(1, '免费版', 'free', '基础功能,适合个人用户', 0.00, 10, 10737418240, 104857600, 50, '{"share":true,"preview":true,"recycle":true}', 1, 1, 1, 1),
(2, '标准版', 'standard', '标准功能,适合小团队', 99.00, 50, 107374182400, 524288000, 200, '{"share":true,"preview":true,"recycle":true,"department":true}', 1, 2, 1, 1),
(3, '专业版', 'professional', '全部功能,适合企业用户', 299.00, 500, 1099511627776, 1073741824, 9999, '{"share":true,"preview":true,"recycle":true,"department":true,"rbac":true,"audit":true}', 1, 3, 1, 1);

-- ----------------------------
-- 初始化系统内置角色
-- ----------------------------
INSERT INTO `r_pan_role` (`role_id`, `tenant_id`, `role_name`, `role_code`, `role_type`, `role_desc`, `data_scope`, `status`, `sort_order`, `create_user`, `update_user`)
VALUES 
(1, 0, '系统管理员', 'system_admin', 1, '系统最高管理员,拥有所有权限', 3, 1, 1, 1, 1),
(2, 0, '租户管理员', 'tenant_admin', 1, '租户管理员,管理租户内所有资源', 2, 1, 2, 1, 1),
(3, 0, '普通用户', 'normal_user', 1, '普通用户,只能管理自己的文件', 0, 1, 3, 1, 1);

-- ----------------------------
-- 初始化基础权限数据
-- ----------------------------
INSERT INTO `r_pan_permission` (`permission_id`, `parent_id`, `permission_name`, `permission_code`, `permission_type`, `path`, `component`, `icon`, `sort_order`, `status`)
VALUES 
-- 一级菜单
(1, 0, '文件管理', 'file', 0, '/file', 'layout/index', 'file', 1, 1),
(2, 0, '分享管理', 'share', 0, '/share', 'layout/index', 'share', 2, 1),
(3, 0, '回收站', 'recycle', 0, '/recycle', 'layout/index', 'delete', 3, 1),
(4, 0, '系统管理', 'system', 0, '/system', 'layout/index', 'setting', 99, 1),

-- 文件管理子菜单
(101, 1, '文件列表', 'file:list', 0, '/file/list', 'list-page/file/index', '', 1, 1),
(102, 1, '图片列表', 'file:image', 0, '/file/image', 'list-page/img/index', '', 2, 1),
(103, 1, '文档列表', 'file:doc', 0, '/file/doc', 'list-page/doc/index', '', 3, 1),
(104, 1, '视频列表', 'file:video', 0, '/file/video', 'list-page/video/index', '', 4, 1),
(105, 1, '音乐列表', 'file:music', 0, '/file/music', 'list-page/music/index', '', 5, 1),

-- 文件操作按钮权限
(201, 101, '上传文件', 'file:upload', 1, '', '', '', 1, 1),
(202, 101, '下载文件', 'file:download', 1, '', '', '', 2, 1),
(203, 101, '删除文件', 'file:delete', 1, '', '', '', 3, 1),
(204, 101, '重命名', 'file:rename', 1, '', '', '', 4, 1),
(205, 101, '移动文件', 'file:move', 1, '', '', '', 5, 1),
(206, 101, '创建文件夹', 'file:create_folder', 1, '', '', '', 6, 1),
(207, 101, '分享文件', 'file:share', 1, '', '', '', 7, 1),

-- 分享管理子菜单
(301, 2, '我的分享', 'share:my', 0, '/share/my', 'list-page/share/index', '', 1, 1),

-- 回收站子菜单
(401, 3, '回收站列表', 'recycle:list', 0, '/recycle/list', 'list-page/recycle/index', '', 1, 1),
(402, 3, '恢复文件', 'recycle:restore', 1, '', '', '', 1, 1),
(403, 3, '彻底删除', 'recycle:delete', 1, '', '', '', 2, 1),

-- 系统管理子菜单(仅管理员可见)
(501, 4, '租户管理', 'system:tenant', 0, '/system/tenant', 'system/tenant/index', '', 1, 1),
(502, 4, '部门管理', 'system:dept', 0, '/system/dept', 'system/dept/index', '', 2, 1),
(503, 4, '角色管理', 'system:role', 0, '/system/role', 'system/role/index', '', 3, 1),
(504, 4, '用户管理', 'system:user', 0, '/system/user', 'system/user/index', '', 4, 1),
(505, 4, '权限管理', 'system:permission', 0, '/system/permission', 'system/permission/index', '', 5, 1);

-- ----------------------------
-- 初始化系统管理员角色权限(拥有所有权限)
-- ----------------------------
INSERT INTO `r_pan_role_permission` (`role_id`, `permission_id`)
SELECT 1, permission_id FROM `r_pan_permission`;

-- ----------------------------
-- 初始化普通用户角色权限(基础文件操作权限)
-- ----------------------------
INSERT INTO `r_pan_role_permission` (`role_id`, `permission_id`)
VALUES 
(3, 1), (3, 2), (3, 3),
(3, 101), (3, 102), (3, 103), (3, 104), (3, 105),
(3, 201), (3, 202), (3, 203), (3, 204), (3, 205), (3, 206), (3, 207),
(3, 301),
(3, 401), (3, 402), (3, 403);

SET FOREIGN_KEY_CHECKS = 1;
