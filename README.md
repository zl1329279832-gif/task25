# 采购管理系统 (Procurement Management System)

> 企业级采购全流程管理系统，覆盖询价、比价、下单、收货、对账全链路。

## 技术栈

| 组件 | 版本 | 用途 |
|------|------|------|
| Java | 17 | 运行环境 |
| Spring Boot | 3.2.5 | 应用框架 |
| Spring Security | 6.x | 认证与授权 |
| JWT (jjwt) | 0.12.5 | 无状态认证 |
| MyBatis Plus | 3.5.6 | ORM 框架 |
| MySQL | 8.0+ | 关系数据库 |
| Redis | 6+ | 缓存与分布式锁 |
| Spring Scheduling | - | 定时任务 |

## 项目结构

```
src/main/java/com/procurement/
├── ProcurementApplication.java       # 启动类
├── config/                           # 配置
│   ├── SecurityConfig.java           # Spring Security 配置
│   ├── MyBatisPlusConfig.java        # 分页 + 自动填充
│   └── RedisConfig.java              # Redis 序列化配置
├── security/                         # 安全模块
│   ├── JwtTokenProvider.java         # JWT 生成与解析
│   ├── JwtAuthenticationFilter.java  # JWT 过滤器
│   ├── LoginUser.java                # 登录用户模型
│   └── UserDetailsServiceImpl.java   # 用户加载
├── common/                           # 通用组件
│   ├── Result.java                   # 统一返回结构
│   ├── BaseEntity.java               # 实体基类
│   ├── BusinessException.java        # 业务异常
│   └── GlobalExceptionHandler.java   # 全局异常处理
├── entity/                           # 实体类 (24个)
│   ├── SysUser.java                  # 系统用户
│   ├── Supplier.java                 # 供应商档案
│   ├── Material.java                 # 物料目录
│   ├── Rfq.java / RfqLine.java       # 询价单
│   ├── Quote.java / QuoteLine.java   # 报价单（版本化）
│   ├── Comparison.java               # 比价记录
│   ├── PurchaseOrder.java            # 采购订单
│   ├── Arrival.java / ArrivalLine    # 到货单（含差异）
│   ├── QualityInspection.java        # 质检记录
│   ├── ReturnOrder.java              # 退货单
│   ├── Invoice.java                  # 发票
│   ├── Reconciliation.java           # 对账单
│   ├── Approval.java                 # 审批流
│   ├── AuditLog.java                 # 审计日志
│   └── Reminder.java                 # 提醒
├── mapper/                           # MyBatis Mapper (24个)
├── service/                          # 业务层
│   ├── AuthService.java              # 认证
│   ├── SupplierService.java          # 供应商管理
│   ├── MaterialService.java          # 物料管理
│   ├── RfqService.java               # 询价管理
│   ├── QuoteService.java             # 报价管理（版本+冻结）
│   ├── ComparisonService.java        # 比价引擎
│   ├── PurchaseOrderService.java     # 采购订单
│   ├── ArrivalService.java           # 收货（分批+差异）
│   ├── QualityInspectionService.java # 质检
│   ├── ReturnOrderService.java       # 退货
│   ├── InvoiceService.java           # 发票登记
│   └── ReconciliationService.java    # 对账
├── controller/                       # REST API (11个)
├── state/                            # 状态机
│   ├── PurchaseOrderStateMachine.java
│   └── RfqStateMachine.java
├── audit/                            # 审计组件
│   ├── @Auditable                    # 审计注解
│   └── AuditAspect.java              # AOP 切面
└── task/
    └── ProcurementScheduler.java     # 定时任务
```

## 数据库设计

共 24 张表，详见 `src/main/resources/db/schema.sql`

### 核心表关系

```
supplier ──┬── rfq_supplier ──→ rfq ──→ rfq_line
           │                     │
           │                     ├──→ quote (版本化) ──→ quote_line
           │                     │
           │                     └──→ comparison ──→ comparison_line
           │
           └── purchase_order ──┬──→ purchase_order_line
                 │              │
                 │              ├──→ arrival ──→ arrival_line (含差异)
                 │              │                    │
                 │              │                    └──→ quality_inspection
                 │              │
                 │              ├──→ return_order ──→ return_line
                 │              │
                 │              ├──→ invoice ──→ invoice_line
                 │              │
                 │              └──→ reconciliation ──→ reconciliation_line
                 │
                 └──→ approval (审批流)

audit_log (操作审计)    reminder (超时提醒)
```

## 角色权限

| 角色 | 代码 | 权限范围 |
|------|------|----------|
| 采购员 | `PURCHASER` | 创建询价单/报价邀请/采购订单，提交审批 |
| 采购主管 | `PURCHASE_MANAGER` | 审批订单/比价，管理供应商，关闭询价 |
| 供应商 | `SUPPLIER` | 提交/修改报价（截止前），登记发票 |
| 仓库人员 | `WAREHOUSE` | 收货登记，质检，发起退货 |
| 财务 | `FINANCE` | 发票审核，生成对账单，对账审批 |

## 核心业务规则

### 1. 报价版本管理与冻结

- 供应商每次修改报价自动递增版本号 (`version` 字段)
- 报价截止后系统自动冻结所有报价（`frozen=1`），供应商无法再改价
- 冻结通过定时任务 `ProcurementScheduler.freezeExpiredQuotes()` 每10分钟扫描
- 使用 Redis 分布式锁防止并发提交

### 2. 采购订单状态机

```
DRAFT → PENDING_APPROVAL → APPROVED → CONFIRMED → PARTIAL_RECEIVED → RECEIVED
                          ↘ REJECTED → DRAFT (可重新提交)

已收货订单 (PARTIAL_RECEIVED / RECEIVED) 禁止取消
```

### 3. 分批到货差异记录

- 每次到货生成独立批次 (`batch_no` 自增)
- `arrival_line.diff_qty = arrived_qty - ordered_qty` 由数据库生成列自动计算
- 到货自动累加 PO 行的 `received_qty`，并联动更新订单状态
- 全部收齐 → `RECEIVED`；部分收齐 → `PARTIAL_RECEIVED`

### 4. 比价规则

| 规则 | 说明 |
|------|------|
| `LOWEST_PRICE` | 纯价格排序，最低价100分，其余按比例 |
| `COMPREHENSIVE` | 综合评分 = 价格(60%) + 交期(40%) |

### 5. 对账规则

对账单自动生成，三单匹配：
- **订单金额** = `SUM(po_line.quantity × po_line.unit_price)`
- **收货金额** = `SUM(arrival_line.accepted_qty × po_line.unit_price)`
- **发票金额** = `SUM(invoice.amount)`
- **差异** = `invoice_amount - receipt_amount`
- 差异为零 → `MATCHED`；否则 → `DIFFERENT`

### 6. 超时提醒

| 定时任务 | 频率 | 说明 |
|----------|------|------|
| 报价截止冻结 | 每10分钟 | 扫描已过截止时间的询价单，冻结报价 |
| 审批超时 | 每小时 | 超48小时未审批的单据生成提醒 |
| 到货超时 | 每天8:00 | 已确认超30天未到货的订单生成提醒 |

### 7. 操作审计

- 使用 `@Auditable` 注解 + AOP 切面自动记录
- 记录内容：操作人、操作类型、实体类型/ID、参数详情、IP 地址
- 所有核心写操作（创建/修改/审批/取消）均纳入审计

## API 接口一览

### 认证
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 登录获取 JWT |

### 供应商
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/suppliers` | 创建供应商 |
| PUT | `/api/suppliers/{id}` | 更新供应商 |
| PUT | `/api/suppliers/{id}/disable` | 停用 |
| PUT | `/api/suppliers/{id}/blacklist` | 拉黑 |
| GET | `/api/suppliers/{id}` | 查询详情 |
| GET | `/api/suppliers` | 分页查询 |

### 物料
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/materials` | 创建物料 |
| PUT | `/api/materials/{id}` | 更新物料 |
| GET | `/api/materials` | 分页查询 |

### 询价单
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/rfq` | 创建询价单 |
| PUT | `/api/rfq/{id}/publish` | 发布 |
| PUT | `/api/rfq/{id}/close` | 关闭 |
| PUT | `/api/rfq/{id}/cancel` | 取消 |
| GET | `/api/rfq/{id}` | 查询详情 |
| GET | `/api/rfq/{id}/lines` | 查询行项 |

### 报价
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/quotes/submit` | 提交报价（自动版本管理） |
| GET | `/api/quotes/rfq/{rfqId}` | 查询某询价单的所有报价 |
| GET | `/api/quotes/{quoteId}/lines` | 查询报价行项 |

### 比价
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/comparisons` | 创建比价 |
| PUT | `/api/comparisons/{id}/select` | 选定供应商 |
| PUT | `/api/comparisons/{id}/approve` | 审批比价结果 |

### 采购订单
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/purchase-orders` | 创建订单 |
| PUT | `/api/purchase-orders/{id}/submit` | 提交审批 |
| PUT | `/api/purchase-orders/{id}/approve` | 审批通过 |
| PUT | `/api/purchase-orders/{id}/reject` | 审批驳回 |
| PUT | `/api/purchase-orders/{id}/confirm` | 确认订单 |
| PUT | `/api/purchase-orders/{id}/cancel` | 取消订单 |

### 到货
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/arrivals` | 创建到货记录 |
| PUT | `/api/arrivals/{id}/status` | 更新状态 |
| GET | `/api/arrivals/po/{poId}` | 查询某订单的所有到货批次 |

### 质检
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/inspections` | 创建质检记录 |
| GET | `/api/inspections/arrival/{arrivalId}` | 按到货查质检 |

### 退货
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/returns` | 创建退货单 |
| PUT | `/api/returns/{id}/approve` | 审批 |
| PUT | `/api/returns/{id}/mark-returned` | 标记已退货 |

### 发票
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/invoices` | 登记发票 |
| PUT | `/api/invoices/{id}/verify` | 验证 |
| GET | `/api/invoices/po/{poId}` | 按订单查发票 |

### 对账
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/reconciliations/generate/{poId}` | 生成对账单 |
| PUT | `/api/reconciliations/{id}/approve` | 审批 |
| PUT | `/api/reconciliations/{id}/reject` | 驳回 |
| GET | `/api/reconciliations/{id}/lines` | 对账行项明细 |

## 快速启动

### 前置条件

- JDK 17+
- MySQL 8.0+
- Redis 6+
- Maven 3.8+

### 1. 初始化数据库

```sql
-- 执行建表脚本
mysql -u root -p < src/main/resources/db/schema.sql

-- 导入测试数据
mysql -u root -p < src/main/resources/db/data.sql
```

### 2. 配置数据库连接

编辑 `src/main/resources/application.yml`，修改：
- `spring.datasource.url` / `username` / `password`
- `spring.data.redis.host` / `port`

### 3. 构建与运行

```bash
# 编译
mvn clean package -DskipTests

# 运行
java -jar target/procurement-system-1.0.0-SNAPSHOT.jar

# 或开发模式
mvn spring-boot:run
```

### 4. 测试账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| purchaser01 | password123 | 采购员 |
| manager01 | password123 | 采购主管 |
| supplier01 | password123 | 供应商(华东钢铁) |
| supplier02 | password123 | 供应商(南方电子) |
| warehouse01 | password123 | 仓库人员 |
| finance01 | password123 | 财务 |

### 5. 运行单元测试

```bash
mvn test
```

测试覆盖（6个测试类，25+ 测试用例）：
- `PurchaseOrderServiceTest` - 订单创建、审批、取消、状态机
- `QuoteServiceTest` - 报价版本管理、冻结、截止后拒绝修改
- `ArrivalServiceTest` - 分批到货、差异计算、PO 状态联动
- `ReconciliationServiceTest` - 对账生成、三单匹配、差异检测
- `RfqServiceTest` - 询价单状态机、发布/关闭
- `ComparisonServiceTest` - 比价规则、排名

## 典型业务流程

```
1. 采购员创建询价单 (RFQ) → 邀请供应商 → 发布
2. 供应商收到邀请 → 提交报价 (v1) → 修改报价 (v2)
3. 报价截止 → 系统自动冻结所有报价
4. 采购员发起比价 → 系统自动评分排名 → 采购主管审批
5. 采购员根据比价结果创建采购订单 → 提交审批
6. 采购主管审批通过 → 采购员确认订单 → 通知供应商
7. 供应商分批发货 → 仓库人员收货登记（自动生成差异记录）
8. 仓库人员质检 → 合格/不合格/有条件接受
9. 不合格品 → 发起退货 → 主管审批 → 执行退货
10. 供应商登记发票 → 财务验证
11. 财务生成对账单 → 三单匹配 → 差异处理 → 审批完成
```

## 许可证

MIT License
