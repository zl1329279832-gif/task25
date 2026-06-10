# 采购管理系统 (Procurement Management System)

基于 Spring Boot 3.2 的采购全流程管理系统后端服务，覆盖询价、报价、比价、下单、收货、质检、退货、发票、对账等完整业务链路。

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17 | LTS 版本 |
| Spring Boot | 3.2.5 | 核心框架 |
| Spring Security | 6.x | JWT 无状态认证 |
| MyBatis Plus | 3.5.5 | ORM + 分页 + 逻辑删除 |
| MySQL | 8.0+ | 主数据库 |
| Redis | 6.x+ | 令牌黑名单 / 分布式锁 / 序列号生成 |
| JJWT | 0.12.5 | JWT 令牌 |
| Lombok | - | 代码简化 |
| H2 | - | 单元测试内存库 |

## 快速启动

### 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.0+
- Redis 6.x+

### 1. 初始化数据库

```sql
source src/main/resources/db/schema.sql
```

### 2. 修改配置

编辑 `src/main/resources/application-dev.yml`，配置 MySQL 和 Redis 连接信息。

### 3. 启动服务

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 4. 运行测试

```bash
mvn test
```

## 系统角色

| 角色 | 编码 | 权限范围 |
|------|------|----------|
| 采购员 | BUYER | 创建询价、下单、查看订单 |
| 采购主管 | PURCHASE_MANAGER | 审批订单、管理供应商和物料 |
| 供应商 | SUPPLIER | 查看询价、提交报价、确认订单和退货 |
| 仓库人员 | WAREHOUSE | 收货、质检、退货发运 |
| 财务 | FINANCE | 发票登记/审核、对账 |

## 核心业务模块

### 1. 认证授权 (`/api/auth`)
- POST `/login` - 登录获取 JWT 令牌
- POST `/logout` - 登出（令牌加入 Redis 黑名单）
- GET `/profile` - 获取当前用户信息
- PUT `/password` - 修改密码

### 2. 用户管理 (`/api/users`)
- CRUD 操作，仅采购主管可操作

### 3. 供应商管理 (`/api/suppliers`)
- 供应商档案 CRUD，资质审核，合格供应商列表
- 字段：编码、名称、联系人、资质状态、银行信息、评分

### 4. 物料管理
- 分类管理 (`/api/material-categories`) - 支持树形结构
- 物料管理 (`/api/materials`) - 按分类查询，含规格、单位、参考价

### 5. 询价管理 (`/api/inquiries`)
- 状态机：`DRAFT -> PUBLISHED -> QUOTING -> CLOSED / CANCELLED`
- 创建询价单（含明细行 + 邀请供应商）
- 发布、关闭、取消操作
- 供应商视角查询（仅看到被邀请的询价）

### 6. 报价管理 (`/api/quotations`)
- 状态机：`DRAFT -> SUBMITTED -> FROZEN -> SELECTED / REJECTED`
- 版本控制：同一供应商可多次报价，自动递增版本号
- **报价冻结**：询价截止后定时任务自动冻结所有已提交报价，供应商不可再改价

### 7. 比价管理 (`/api/comparisons`)
- 策略模式：最低价策略 / 综合评分策略（价格 70% + 基础评分 30%）
- 自动标记中选/落选报价

### 8. 采购订单 (`/api/orders`)
- 状态机：`PENDING_APPROVAL -> APPROVED -> CONFIRMED -> PARTIAL_DELIVERED -> DELIVERED -> COMPLETED / CANCELLED`
- **自动审批**：金额 <= 10,000 自动通过；10,000~100,000 需一级审批；>100,000 需二级审批
- **取消限制**：存在已收货明细行（received_quantity > 0）时不允许整单取消

### 9. 到货管理 (`/api/deliveries`)
- 支持分批到货
- **到货差异**：实际数量 != 预期数量时自动生成差异记录（SHORTAGE / EXCESS）
- 自动更新订单明细行已收数量

### 10. 质检管理 (`/api/inspections`)
- 质检结果：合格(QUALIFIED) / 不合格(UNQUALIFIED) / 让步接收(CONCESSION_ACCEPT)
- 根据结果自动更新到货单状态

### 11. 退货管理 (`/api/returns`)
- 状态机：`PENDING -> SUPPLIER_CONFIRMED -> RETURNING -> COMPLETED`
- 自动计算退货总金额

### 12. 发票管理 (`/api/invoices`)
- 发票登记（普通/专用发票）、审核、驳回
- 支持按订单、状态查询

### 13. 对账管理 (`/api/reconciliations`)
- 状态机：`DRAFT -> CONFIRMED -> SETTLED / DISPUTED`
- **三单匹配**：订单金额 vs 到货金额 vs 发票金额
- 差异类型：ORDER_DELIVERY / DELIVERY_INVOICE / ALL / NONE
- 退货金额自动扣减

### 14. 审批记录 (`/api/approvals`)
- 记录所有审批操作，支持按业务类型查询

## 定时任务

| 任务 | 频率 | 说明 |
|------|------|------|
| QuotationFreezeJob | 每5分钟 | 冻结到期询价的所有已提交报价 |
| OrderTimeoutReminderJob | 每天 9:00 | 提醒超过48小时未确认的订单 |
| ReconciliationGenerateJob | 每月1日 2:00 | 自动生成上月对账单 |

## 审计日志

- 基于 AOP 切面，通过 `@AuditLog` 注解自动记录操作
- 记录内容：操作人、模块、操作类型、业务参数、IP 地址、时间

## 数据库表 (27张)

| 模块 | 表 | 说明 |
|------|-----|------|
| 系统 | sys_user, sys_role, sys_user_role | 用户与角色 |
| 供应商 | supplier | 供应商档案 |
| 物料 | material_category, material | 分类树 + 物料 |
| 询价 | inquiry, inquiry_item, inquiry_supplier | 询价单 + 明细 + 邀请 |
| 报价 | quotation, quotation_item | 报价 + 版本控制 |
| 比价 | comparison, comparison_item | 策略比价 |
| 订单 | purchase_order, order_item | 采购订单 + 明细 |
| 到货 | delivery, delivery_item, delivery_diff | 到货 + 差异 |
| 质检 | inspection | 质检记录 |
| 退货 | return_order, return_item | 退货流程 |
| 发票 | invoice, invoice_item | 发票管理 |
| 对账 | reconciliation, reconciliation_item | 三单匹配对账 |
| 审批 | approval_record | 审批记录 |
| 审计 | audit_log | 操作日志 |

## 测试数据

`src/test/resources/data.sql` 包含测试数据：
- 5 个系统角色
- 5 个测试用户（admin/buyer01/supplier01/warehouse01/finance01），密码均为 `admin123`
- 3 个供应商
- 5 个物料分类 + 4 个物料

## 单元测试

共 13 个测试类，覆盖：
- 3 个状态机测试：合法转换成功 + 非法转换抛异常
- 1 个工具类测试：序列号生成格式验证
- 9 个 Service 测试：核心业务逻辑（审批阈值、到货差异、报价冻结、质检状态联动等）

## 项目结构

```
src/main/java/com/procurement/
├── ProcurementApplication.java        # 启动类
├── config/                            # 配置（Security, Redis, MyBatisPlus, Scheduling, WebMvc）
├── security/                          # JWT + Filter + UserDetailsService
├── common/                            # Result, Exception, Enums, BaseEntity, 切面, 工具类
├── audit/                             # 审计日志
├── job/                               # 3个定时任务
└── module/
    ├── auth/                          # 登录认证
    ├── system/                        # 用户/角色管理
    ├── supplier/                      # 供应商管理
    ├── material/                      # 物料分类 + 物料
    ├── inquiry/                       # 询价（含状态机）
    ├── quotation/                     # 报价（版本控制 + 冻结）
    ├── comparison/                    # 比价（策略模式）
    ├── order/                         # 采购订单（状态机 + 审批流）
    ├── delivery/                      # 到货（差异记录）
    ├── inspection/                    # 质检
    ├── returns/                       # 退货（状态机）
    ├── invoice/                       # 发票
    ├── reconciliation/                # 对账（三单匹配）
    └── approval/                      # 审批记录
```
