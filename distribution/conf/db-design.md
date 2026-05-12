# SaaS多租户云盘系统 - 数据库设计文档

## 📋 文档信息

| 项目 | 内容 |
|------|------|
| **版本** | v2.0 |
| **创建日期** | 2026-05-12 |
| **数据库** | MySQL 8.0+ |
| **字符集** | utf8mb4 |
| **排序规则** | utf8mb4_bin |

---

## 🎯 设计目标

1. **多租户数据隔离**: 通过`tenant_id`实现行级数据隔离
2. **组织架构支持**: 支持树状部门结构和多部门关联
3. **RBAC权限体系**: 实现租户级别的角色权限管理
4. **套餐订阅管理**: 支持不同级别的租户套餐和订阅
5. **存储配额控制**: 实现租户级别存储空间限制

---

## 📊 数据库架构概览

```
┌─────────────────────────────────────────────────────────┐
│                    租户管理模块                           │
│  r_pan_tenant │ r_pan_tenant_package │ r_pan_tenant_subscription │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│                    组织架构模块                           │
│        r_pan_department │ r_pan_user_department          │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│                    RBAC权限模块                           │
│   r_pan_role │ r_pan_permission │ r_pan_role_permission │
│                    r_pan_user_role                       │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│                    业务数据模块                           │
│  r_pan_user │ r_pan_file │ r_pan_user_file │ r_pan_share │
└─────────────────────────────────────────────────────────┘
```

---

## 📦 表结构详细说明

### 1. 租户管理模块

#### 1.1 r_pan_tenant (租户信息表)

**用途**: 存储租户基本信息和配置

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| tenant_id | bigint(20) | ✓ | - | 租户ID(主键) |
| tenant_code | varchar(64) | ✓ | '' | 租户编码(唯一标识) |
| tenant_name | varchar(128) | ✓ | '' | 租户名称 |
| tenant_domain | varchar(128) | - | '' | 租户专属域名(可选) |
| contact_name | varchar(64) | ✓ | '' | 联系人姓名 |
| contact_phone | varchar(32) | ✓ | '' | 联系人电话 |
| contact_email | varchar(128) | ✓ | '' | 联系人邮箱 |
| logo_url | varchar(255) | - | '' | 租户Logo地址 |
| theme_color | varchar(32) | - | #1890ff | 主题色 |
| status | tinyint(1) | ✓ | 0 | 租户状态(0 待审核 1 正常 2 冻结 3 已过期) |
| expire_time | datetime | - | NULL | 租户过期时间(NULL表示永久) |
| max_user_count | int(11) | ✓ | 10 | 最大用户数限制 |
| max_storage_size | bigint(20) | ✓ | 10GB | 最大存储空间(字节) |
| used_storage_size | bigint(20) | ✓ | 0 | 已使用存储空间(字节) |
| user_count | int(11) | ✓ | 0 | 当前用户数 |
| features | varchar(1024) | - | '' | 功能特性(JSON格式) |
| remark | varchar(512) | - | '' | 备注 |
| create_user | bigint(20) | ✓ | - | 创建人ID |
| create_time | datetime | ✓ | CURRENT_TIMESTAMP | 创建时间 |
| update_user | bigint(20) | ✓ | - | 更新人ID |
| update_time | datetime | ✓ | CURRENT_TIMESTAMP | 更新时间 |

**索引设计**:
- PRIMARY KEY: `tenant_id`
- UNIQUE: `tenant_code`
- UNIQUE: `tenant_domain`
- INDEX: `status`
- INDEX: `expire_time`

**业务规则**:
1. 租户编码全局唯一,建议使用英文+数字组合
2. 租户域名可选,用于实现租户独立访问入口
3. 存储空间单位统一为字节
4. features字段存储JSON格式的功能开关配置

---

#### 1.2 r_pan_tenant_package (租户套餐表)

**用途**: 定义不同级别的租户套餐

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| package_id | bigint(20) | ✓ | - | 套餐ID(主键) |
| package_name | varchar(64) | ✓ | '' | 套餐名称 |
| package_code | varchar(32) | ✓ | '' | 套餐编码 |
| package_desc | varchar(512) | ✓ | '' | 套餐描述 |
| price | decimal(10,2) | ✓ | 0.00 | 套餐价格(元/月) |
| max_user_count | int(11) | ✓ | 10 | 最大用户数 |
| max_storage_size | bigint(20) | ✓ | 10GB | 最大存储空间(字节) |
| max_file_size | bigint(20) | ✓ | 100MB | 单文件最大大小(字节) |
| max_share_count | int(11) | ✓ | 100 | 最大分享数 |
| features | varchar(1024) | - | '' | 功能特性(JSON格式) |
| status | tinyint(1) | ✓ | 1 | 状态(0 禁用 1 启用) |
| sort_order | int(11) | ✓ | 0 | 排序 |
| create_user | bigint(20) | ✓ | - | 创建人ID |
| create_time | datetime | ✓ | CURRENT_TIMESTAMP | 创建时间 |
| update_user | bigint(20) | ✓ | - | 更新人ID |
| update_time | datetime | ✓ | CURRENT_TIMESTAMP | 更新时间 |

**索引设计**:
- PRIMARY KEY: `package_id`
- UNIQUE: `package_code`
- INDEX: `status`

**预设套餐示例**:
```json
{
  "免费版": {
    "price": 0,
    "max_user_count": 10,
    "max_storage_size": "10GB",
    "max_file_size": "100MB",
    "features": ["share", "preview", "recycle"]
  },
  "标准版": {
    "price": 99,
    "max_user_count": 50,
    "max_storage_size": "100GB",
    "max_file_size": "500MB",
    "features": ["share", "preview", "recycle", "department"]
  },
  "专业版": {
    "price": 299,
    "max_user_count": 500,
    "max_storage_size": "1TB",
    "max_file_size": "1GB",
    "features": ["share", "preview", "recycle", "department", "rbac", "audit"]
  }
}
```

---

#### 1.3 r_pan_tenant_subscription (租户订阅表)

**用途**: 记录租户的订阅历史和状态

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| subscription_id | bigint(20) | ✓ | - | 订阅ID(主键) |
| tenant_id | bigint(20) | ✓ | - | 租户ID |
| package_id | bigint(20) | ✓ | - | 套餐ID |
| start_time | datetime | ✓ | - | 订阅开始时间 |
| end_time | datetime | ✓ | - | 订阅结束时间 |
| status | tinyint(1) | ✓ | 0 | 订阅状态(0 未生效 1 生效中 2 已过期 3 已取消) |
| auto_renew | tinyint(1) | ✓ | 0 | 是否自动续费(0 否 1 是) |
| order_no | varchar(64) | ✓ | '' | 订单号 |
| price | decimal(10,2) | ✓ | 0.00 | 实际支付价格 |
| remark | varchar(512) | - | '' | 备注 |
| create_user | bigint(20) | ✓ | - | 创建人ID |
| create_time | datetime | ✓ | CURRENT_TIMESTAMP | 创建时间 |
| update_user | bigint(20) | ✓ | - | 更新人ID |
| update_time | datetime | ✓ | CURRENT_TIMESTAMP | 更新时间 |

**索引设计**:
- PRIMARY KEY: `subscription_id`
- INDEX: `tenant_id`
- INDEX: `package_id`
- INDEX: `status`
- INDEX: `end_time`
- UNIQUE: `order_no`

---

### 2. 组织架构模块

#### 2.1 r_pan_department (部门表)

**用途**: 存储租户的部门组织架构

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| dept_id | bigint(20) | ✓ | - | 部门ID(主键) |
| tenant_id | bigint(20) | ✓ | - | 租户ID |
| parent_id | bigint(20) | ✓ | 0 | 父部门ID(顶级部门为0) |
| dept_name | varchar(64) | ✓ | '' | 部门名称 |
| dept_code | varchar(32) | ✓ | '' | 部门编码 |
| leader_user_id | bigint(20) | - | NULL | 部门负责人ID |
| phone | varchar(32) | - | '' | 联系电话 |
| email | varchar(128) | - | '' | 邮箱 |
| sort_order | int(11) | ✓ | 0 | 排序 |
| status | tinyint(1) | ✓ | 1 | 状态(0 禁用 1 启用) |
| del_flag | tinyint(1) | ✓ | 0 | 删除标识(0 否 1 是) |
| create_user | bigint(20) | ✓ | - | 创建人ID |
| create_time | datetime | ✓ | CURRENT_TIMESTAMP | 创建时间 |
| update_user | bigint(20) | ✓ | - | 更新人ID |
| update_time | datetime | ✓ | CURRENT_TIMESTAMP | 更新时间 |

**索引设计**:
- PRIMARY KEY: `dept_id`
- INDEX: `tenant_id`
- INDEX: `parent_id`
- UNIQUE: `tenant_id + dept_code`
- INDEX: `status`

**树形结构示例**:
```
默认部门 (dept_id=1, parent_id=0)
├── 技术部 (dept_id=2, parent_id=1)
│   ├── 前端组 (dept_id=4, parent_id=2)
│   └── 后端组 (dept_id=5, parent_id=2)
├── 产品部 (dept_id=3, parent_id=1)
└── 市场部 (dept_id=6, parent_id=1)
```

---

#### 2.2 r_pan_user_department (用户部门关联表)

**用途**: 实现用户与部门的多对多关系

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| id | bigint(20) | ✓ | AUTO_INCREMENT | 主键 |
| tenant_id | bigint(20) | ✓ | - | 租户ID |
| user_id | bigint(20) | ✓ | - | 用户ID |
| dept_id | bigint(20) | ✓ | - | 部门ID |
| is_primary | tinyint(1) | ✓ | 1 | 是否主部门(0 否 1 是) |
| position | varchar(64) | - | '' | 职位 |
| create_time | datetime | ✓ | CURRENT_TIMESTAMP | 创建时间 |

**索引设计**:
- PRIMARY KEY: `id`
- INDEX: `tenant_id`
- INDEX: `user_id`
- INDEX: `dept_id`
- UNIQUE: `tenant_id + user_id + dept_id`

**业务规则**:
1. 一个用户可以属于多个部门
2. 每个用户在租户内只能有一个主部门
3. 跨租户的部门关联不允许

---

### 3. RBAC权限模块

#### 3.1 r_pan_role (角色表)

**用途**: 定义租户内的角色

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| role_id | bigint(20) | ✓ | - | 角色ID(主键) |
| tenant_id | bigint(20) | ✓ | - | 租户ID |
| role_name | varchar(64) | ✓ | '' | 角色名称 |
| role_code | varchar(32) | ✓ | '' | 角色编码 |
| role_type | tinyint(1) | ✓ | 0 | 角色类型(0 自定义 1 系统内置) |
| role_desc | varchar(256) | ✓ | '' | 角色描述 |
| data_scope | tinyint(1) | ✓ | 0 | 数据权限范围(0 仅本人 1 本部门 2 本部门及下级 3 全租户) |
| status | tinyint(1) | ✓ | 1 | 状态(0 禁用 1 启用) |
| sort_order | int(11) | ✓ | 0 | 排序 |
| del_flag | tinyint(1) | ✓ | 0 | 删除标识(0 否 1 是) |
| create_user | bigint(20) | ✓ | - | 创建人ID |
| create_time | datetime | ✓ | CURRENT_TIMESTAMP | 创建时间 |
| update_user | bigint(20) | ✓ | - | 更新人ID |
| update_time | datetime | ✓ | CURRENT_TIMESTAMP | 更新时间 |

**索引设计**:
- PRIMARY KEY: `role_id`
- INDEX: `tenant_id`
- UNIQUE: `tenant_id + role_code`
- INDEX: `status`

**预设角色**:
1. **系统管理员** (system_admin): 拥有所有权限,可管理所有租户
2. **租户管理员** (tenant_admin): 管理租户内所有资源
3. **普通用户** (normal_user): 只能管理自己的文件

---

#### 3.2 r_pan_permission (权限表)

**用途**: 定义系统所有权限点

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| permission_id | bigint(20) | ✓ | - | 权限ID(主键) |
| parent_id | bigint(20) | ✓ | 0 | 父权限ID(顶级权限为0) |
| permission_name | varchar(64) | ✓ | '' | 权限名称 |
| permission_code | varchar(128) | ✓ | '' | 权限编码(如:file:upload) |
| permission_type | tinyint(1) | ✓ | 0 | 权限类型(0 菜单 1 按钮 2 接口) |
| path | varchar(255) | - | '' | 路由路径 |
| component | varchar(255) | - | '' | 组件路径 |
| icon | varchar(64) | - | '' | 图标 |
| sort_order | int(11) | ✓ | 0 | 排序 |
| status | tinyint(1) | ✓ | 1 | 状态(0 禁用 1 启用) |
| create_time | datetime | ✓ | CURRENT_TIMESTAMP | 创建时间 |
| update_time | datetime | ✓ | CURRENT_TIMESTAMP | 更新时间 |

**索引设计**:
- PRIMARY KEY: `permission_id`
- UNIQUE: `permission_code`
- INDEX: `parent_id`
- INDEX: `status`

**权限编码规范**:
- 菜单权限: `模块名` (如: `file`, `share`)
- 按钮权限: `模块名:操作` (如: `file:upload`, `file:delete`)
- 接口权限: `模块名:接口` (如: `file:api:upload`)

---

#### 3.3 r_pan_role_permission (角色权限关联表)

**用途**: 实现角色与权限的多对多关系

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| id | bigint(20) | ✓ | AUTO_INCREMENT | 主键 |
| role_id | bigint(20) | ✓ | - | 角色ID |
| permission_id | bigint(20) | ✓ | - | 权限ID |
| create_time | datetime | ✓ | CURRENT_TIMESTAMP | 创建时间 |

**索引设计**:
- PRIMARY KEY: `id`
- INDEX: `role_id`
- INDEX: `permission_id`
- UNIQUE: `role_id + permission_id`

---

#### 3.4 r_pan_user_role (用户角色关联表)

**用途**: 实现用户与角色的多对多关系

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| id | bigint(20) | ✓ | AUTO_INCREMENT | 主键 |
| tenant_id | bigint(20) | ✓ | - | 租户ID |
| user_id | bigint(20) | ✓ | - | 用户ID |
| role_id | bigint(20) | ✓ | - | 角色ID |
| create_time | datetime | ✓ | CURRENT_TIMESTAMP | 创建时间 |

**索引设计**:
- PRIMARY KEY: `id`
- INDEX: `tenant_id`
- INDEX: `user_id`
- INDEX: `role_id`
- UNIQUE: `tenant_id + user_id + role_id`

---

### 4. 现有表结构改造说明

#### 4.1 r_pan_user (用户表)

**新增字段**:
- `tenant_id`: 租户ID(0表示系统管理员)
- `nickname`: 用户昵称
- `avatar`: 头像地址
- `phone`: 手机号
- `email`: 邮箱
- `status`: 用户状态(0 禁用 1 启用)
- `user_type`: 用户类型(0 普通用户 1 租户管理员 2 系统管理员)
- `last_login_time`: 最后登录时间
- `last_login_ip`: 最后登录IP
- `del_flag`: 删除标识

**索引变更**:
- 删除: `uk_username`
- 新增: `uk_tenant_username` (tenant_id + username)
- 新增: `idx_tenant_id`
- 新增: `idx_status`

---

#### 4.2 r_pan_file (物理文件表)

**新增字段**:
- `tenant_id`: 租户ID

**新增索引**:
- `idx_tenant_id`
- `idx_tenant_create_user`

---

#### 4.3 r_pan_user_file (用户文件表)

**新增字段**:
- `tenant_id`: 租户ID
- `share_scope`: 共享范围(0 私有 1 部门共享 2 租户共享)

**索引变更**:
- 删除: `index_file_list`
- 新增: `idx_file_list` (包含tenant_id的复合索引)

---

#### 4.4 r_pan_share (分享表)

**新增字段**:
- `tenant_id`: 租户ID
- `share_scope`: 分享范围(0 公开链接 1 租户内 2 部门内)

**索引变更**:
- 删除: `uk_create_user_time`
- 新增: `uk_tenant_create_user_time`

---

#### 4.5 其他表

**r_pan_share_file**, **r_pan_user_search_history**, **r_pan_file_chunk**, **r_pan_error_log**

均新增:
- `tenant_id`: 租户ID
- 对应索引: `idx_tenant_id`

---

## 🔐 数据隔离策略

### 行级隔离

所有业务表通过`tenant_id`实现行级数据隔离:

```sql
-- 查询示例: 只能查询当前租户的数据
SELECT * FROM r_pan_file 
WHERE tenant_id = #{currentTenantId};

-- 插入示例: 自动填充当前租户ID
INSERT INTO r_pan_file (tenant_id, filename, ...)
VALUES (#{currentTenantId}, #{filename}, ...);
```

### MyBatis-Plus租户插件配置

```java
@Configuration
public class MybatisPlusConfig {
    
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        
        // 租户插件
        TenantLineInnerInterceptor tenantInterceptor = new TenantLineInnerInterceptor();
        tenantInterceptor.setTenantLineHandler(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                return new LongValue(TenantContext.getCurrentTenantId());
            }
            
            @Override
            public String getTenantIdColumn() {
                return "tenant_id";
            }
            
            @Override
            public boolean ignoreTable(String tableName) {
                // 不需要租户隔离的表
                return "r_pan_tenant".equals(tableName) 
                    || "r_pan_tenant_package".equals(tableName)
                    || "r_pan_permission".equals(tableName);
            }
        });
        
        interceptor.addInnerInterceptor(tenantInterceptor);
        return interceptor;
    }
}
```

---

## 📈 性能优化建议

### 1. 索引策略

- 所有租户相关查询必须包含`tenant_id`作为第一索引列
- 复合索引按照查询频率排序字段
- 定期分析慢查询日志,优化索引

### 2. 分区策略(可选)

对于大数据量场景,可考虑按租户分区:

```sql
ALTER TABLE r_pan_file 
PARTITION BY HASH(tenant_id) 
PARTITIONS 10;
```

### 3. 缓存策略

- 租户配置信息使用Redis缓存
- 用户权限信息使用本地缓存+Redis二级缓存
- 部门树结构使用Redis Hash存储

---

## 🔒 安全设计

### 1. 数据访问控制

- 所有SQL必须包含`tenant_id`过滤条件
- 通过MyBatis-Plus插件自动注入租户条件
- 禁止应用层动态拼接SQL绕过租户隔离

### 2. 权限校验

- 接口级别权限校验: `@RequirePermission("file:upload")`
- 数据级别权限校验: 根据`data_scope`控制数据访问范围
- 租户管理员只能管理本租户数据

### 3. 审计日志

- 所有关键操作记录`tenant_id`和`user_id`
- 错误日志按租户隔离存储
- 定期审计异常访问行为

---

## 📝 初始化数据说明

### 预设租户

| tenant_id | tenant_code | tenant_name | 说明 |
|-----------|-------------|-------------|------|
| 1 | system | 系统默认租户 | 用于迁移现有数据 |

### 预设套餐

| package_id | package_code | package_name | price | 说明 |
|------------|--------------|--------------|-------|------|
| 1 | free | 免费版 | 0元/月 | 基础功能 |
| 2 | standard | 标准版 | 99元/月 | 适合小团队 |
| 3 | professional | 专业版 | 299元/月 | 全部功能 |

### 预设角色

| role_id | role_code | role_name | 说明 |
|---------|-----------|-----------|------|
| 1 | system_admin | 系统管理员 | 拥有所有权限 |
| 2 | tenant_admin | 租户管理员 | 管理租户内资源 |
| 3 | normal_user | 普通用户 | 基础文件操作 |

### 预设权限

系统初始化包含以下权限树:
- 文件管理(上传、下载、删除、重命名、移动、分享)
- 分享管理(创建分享、查看分享列表)
- 回收站(恢复、彻底删除)
- 系统管理(租户管理、部门管理、角色管理、用户管理、权限管理)

---

## 🚀 部署步骤

### 1. 全新部署

```bash
# 执行完整建表脚本
mysql -u root -p cloud_pan < db-multitenant.sql
```

### 2. 现有系统升级

```bash
# 1. 备份数据库
mysqldump -u root -p cloud_pan > backup_$(date +%Y%m%d).sql

# 2. 执行新表创建脚本
mysql -u root -p cloud_pan < db-multitenant.sql

# 3. 执行数据迁移脚本
mysql -u root -p cloud_pan < db-migration.sql

# 4. 验证迁移结果
mysql -u root -p cloud_pan -e "SELECT COUNT(*) FROM r_pan_tenant;"
```

---

## 📊 数据库ER图

```
r_pan_tenant (1) ──────< (N) r_pan_user
     │                        │
     │                        │
     ├────< r_pan_department  │
     │         │              │
     │         └────< r_pan_user_department >────┘
     │
     ├────< r_pan_file
     │
     ├────< r_pan_share
     │
     ├────< r_pan_role ────< r_pan_user_role >──── r_pan_user
     │              │
     │              └────< r_pan_role_permission >──── r_pan_permission
     │
     └────< r_pan_tenant_subscription >──── r_pan_tenant_package
```

---

## 📌 注意事项

1. **数据迁移前必须备份数据库**
2. **租户ID一旦分配不可修改**
3. **删除租户时需要级联清理所有关联数据**
4. **系统管理员租户(tenant_id=0)不参与租户隔离**
5. **所有时间字段统一使用datetime类型**
6. **金额字段使用decimal类型,避免精度丢失**
7. **状态字段统一使用tinyint,0表示禁用/未生效,1表示启用/生效**

---

## 📞 联系方式

如有问题,请联系开发团队或提交Issue。
