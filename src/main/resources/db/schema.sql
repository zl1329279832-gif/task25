-- ============================================================
-- 采购管理系统数据库 Schema
-- MySQL 8.0+
-- ============================================================

CREATE DATABASE IF NOT EXISTS procurement DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE procurement;

-- -----------------------------------------------------------
-- 1. 用户表 (sys_user)
-- -----------------------------------------------------------
CREATE TABLE sys_user (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(64)  NOT NULL UNIQUE,
    password        VARCHAR(255) NOT NULL,
    real_name       VARCHAR(64),
    phone           VARCHAR(20),
    email           VARCHAR(128),
    role            VARCHAR(32)  NOT NULL COMMENT 'PURCHASER / PURCHASE_MANAGER / SUPPLIER / WAREHOUSE / FINANCE',
    supplier_id     BIGINT       NULL COMMENT '关联供应商ID（供应商角色专用）',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0
) ENGINE=InnoDB COMMENT='系统用户';

-- -----------------------------------------------------------
-- 2. 供应商档案 (supplier)
-- -----------------------------------------------------------
CREATE TABLE supplier (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    code            VARCHAR(32)  NOT NULL UNIQUE COMMENT '供应商编码',
    name            VARCHAR(128) NOT NULL,
    contact_person  VARCHAR(64),
    phone           VARCHAR(20),
    email           VARCHAR(128),
    address         VARCHAR(256),
    bank_name       VARCHAR(128),
    bank_account    VARCHAR(64),
    tax_number      VARCHAR(64),
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DISABLED / BLACKLISTED',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0
) ENGINE=InnoDB COMMENT='供应商档案';

-- -----------------------------------------------------------
-- 3. 物料目录 (material)
-- -----------------------------------------------------------
CREATE TABLE material (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    code            VARCHAR(32)  NOT NULL UNIQUE COMMENT '物料编码',
    name            VARCHAR(128) NOT NULL,
    category        VARCHAR(64),
    unit            VARCHAR(16)  NOT NULL COMMENT '计量单位',
    spec            VARCHAR(256) COMMENT '规格型号',
    description     VARCHAR(512),
    status          TINYINT      NOT NULL DEFAULT 1,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0
) ENGINE=InnoDB COMMENT='物料目录';

-- -----------------------------------------------------------
-- 4. 询价单 (rfq)
-- -----------------------------------------------------------
CREATE TABLE rfq (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    rfq_no          VARCHAR(32)  NOT NULL UNIQUE,
    title           VARCHAR(128),
    purchaser_id    BIGINT       NOT NULL COMMENT '采购员',
    deadline        DATETIME     NOT NULL COMMENT '报价截止时间',
    status          VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT / PUBLISHED / CLOSED / CANCELLED',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0
) ENGINE=InnoDB COMMENT='询价单';

-- -----------------------------------------------------------
-- 5. 询价单行项 (rfq_line)
-- -----------------------------------------------------------
CREATE TABLE rfq_line (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    rfq_id          BIGINT       NOT NULL,
    material_id     BIGINT       NOT NULL,
    quantity        DECIMAL(12,2) NOT NULL,
    description     VARCHAR(256),
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_rfq_id (rfq_id)
) ENGINE=InnoDB COMMENT='询价单行项';

-- -----------------------------------------------------------
-- 6. 询价单-供应商邀请 (rfq_supplier)
-- -----------------------------------------------------------
CREATE TABLE rfq_supplier (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    rfq_id          BIGINT       NOT NULL,
    supplier_id     BIGINT       NOT NULL,
    invited_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_rfq_supplier (rfq_id, supplier_id)
) ENGINE=InnoDB COMMENT='询价单供应商邀请';

-- -----------------------------------------------------------
-- 7. 报价单 (quote) - 版本化
-- -----------------------------------------------------------
CREATE TABLE quote (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    quote_no        VARCHAR(32)  NOT NULL,
    rfq_id          BIGINT       NOT NULL,
    supplier_id     BIGINT       NOT NULL,
    version         INT          NOT NULL DEFAULT 1 COMMENT '版本号',
    total_amount    DECIMAL(14,2),
    status          VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT / SUBMITTED / ACCEPTED / REJECTED / FROZEN',
    frozen          TINYINT      NOT NULL DEFAULT 0 COMMENT '1=已冻结（截止后自动冻结）',
    submitted_at    DATETIME     NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_quote_version (quote_no, version),
    INDEX idx_rfq_supplier (rfq_id, supplier_id)
) ENGINE=InnoDB COMMENT='报价单（支持版本）';

-- -----------------------------------------------------------
-- 8. 报价单行项 (quote_line)
-- -----------------------------------------------------------
CREATE TABLE quote_line (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    quote_id        BIGINT       NOT NULL,
    rfq_line_id     BIGINT       NOT NULL,
    material_id     BIGINT       NOT NULL,
    unit_price      DECIMAL(12,4) NOT NULL,
    quantity         DECIMAL(12,2) NOT NULL,
    delivery_days   INT          COMMENT '交期(天)',
    remark          VARCHAR(256),
    INDEX idx_quote_id (quote_id)
) ENGINE=InnoDB COMMENT='报价单行项';

-- -----------------------------------------------------------
-- 9. 比价记录 (comparison)
-- -----------------------------------------------------------
CREATE TABLE comparison (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    comparison_no   VARCHAR(32)  NOT NULL UNIQUE,
    rfq_id          BIGINT       NOT NULL,
    rule            VARCHAR(32)  NOT NULL DEFAULT 'LOWEST_PRICE' COMMENT 'LOWEST_PRICE / BEST_QUALITY / COMPREHENSIVE',
    result_summary  TEXT,
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / COMPLETED / APPROVED',
    created_by      BIGINT       NOT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='比价记录';

-- -----------------------------------------------------------
-- 10. 比价行项 (comparison_line)
-- -----------------------------------------------------------
CREATE TABLE comparison_line (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    comparison_id   BIGINT       NOT NULL,
    quote_id        BIGINT       NOT NULL,
    supplier_id     BIGINT       NOT NULL,
    total_amount    DECIMAL(14,2),
    avg_delivery    INT,
    score           DECIMAL(6,2) COMMENT '综合评分',
    rank_no         INT          COMMENT '排名',
    selected        TINYINT      NOT NULL DEFAULT 0,
    INDEX idx_comparison_id (comparison_id)
) ENGINE=InnoDB COMMENT='比价行项';

-- -----------------------------------------------------------
-- 11. 采购订单 (purchase_order)
-- -----------------------------------------------------------
CREATE TABLE purchase_order (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    po_no           VARCHAR(32)  NOT NULL UNIQUE,
    supplier_id     BIGINT       NOT NULL,
    comparison_id   BIGINT       NULL COMMENT '关联比价单',
    total_amount    DECIMAL(14,2),
    status          VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT / PENDING_APPROVAL / APPROVED / REJECTED / CONFIRMED / PARTIAL_RECEIVED / RECEIVED / CANCELLED',
    approved_by     BIGINT       NULL,
    approved_at     DATETIME     NULL,
    cancel_reason   VARCHAR(256),
    created_by      BIGINT       NOT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    INDEX idx_supplier (supplier_id),
    INDEX idx_status (status)
) ENGINE=InnoDB COMMENT='采购订单';

-- -----------------------------------------------------------
-- 12. 采购订单行项 (purchase_order_line)
-- -----------------------------------------------------------
CREATE TABLE purchase_order_line (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    po_id           BIGINT       NOT NULL,
    material_id     BIGINT       NOT NULL,
    quantity        DECIMAL(12,2) NOT NULL COMMENT '订购数量',
    received_qty    DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '已收货数量',
    accepted_qty    DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '验收合格数量',
    rejected_qty    DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '质检退回数量',
    unit_price      DECIMAL(12,4) NOT NULL,
    amount          DECIMAL(14,2),
    INDEX idx_po_id (po_id)
) ENGINE=InnoDB COMMENT='采购订单行项';

-- -----------------------------------------------------------
-- 13. 到货单 (arrival)
-- -----------------------------------------------------------
CREATE TABLE arrival (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    arrival_no      VARCHAR(32)  NOT NULL UNIQUE,
    po_id           BIGINT       NOT NULL,
    batch_no        INT          NOT NULL DEFAULT 1 COMMENT '到货批次',
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / INSPECTING / ACCEPTED / PARTIAL_ACCEPTED / REJECTED',
    arrived_at      DATETIME     NOT NULL,
    receiver_id     BIGINT       NOT NULL COMMENT '收货人',
    remark          VARCHAR(256),
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_po_id (po_id)
) ENGINE=InnoDB COMMENT='到货单';

-- -----------------------------------------------------------
-- 14. 到货行项 (arrival_line)
-- -----------------------------------------------------------
CREATE TABLE arrival_line (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    arrival_id      BIGINT       NOT NULL,
    po_line_id      BIGINT       NOT NULL,
    material_id     BIGINT       NOT NULL,
    ordered_qty     DECIMAL(12,2) NOT NULL COMMENT '订单数量',
    arrived_qty     DECIMAL(12,2) NOT NULL COMMENT '到货数量',
    accepted_qty    DECIMAL(12,2) DEFAULT 0 COMMENT '验收合格数量',
    diff_qty        DECIMAL(12,2) GENERATED ALWAYS AS (arrived_qty - ordered_qty) STORED COMMENT '差异数量',
    diff_remark     VARCHAR(256) COMMENT '差异说明',
    INDEX idx_arrival_id (arrival_id)
) ENGINE=InnoDB COMMENT='到货行项（含差异记录）';

-- -----------------------------------------------------------
-- 15. 质检记录 (quality_inspection)
-- -----------------------------------------------------------
CREATE TABLE quality_inspection (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    inspection_no   VARCHAR(32)  NOT NULL UNIQUE,
    arrival_id      BIGINT       NOT NULL,
    inspector_id    BIGINT       NOT NULL,
    result          VARCHAR(16)  NOT NULL COMMENT 'PASS / FAIL / CONDITIONAL',
    remark          VARCHAR(512),
    inspected_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_arrival_id (arrival_id)
) ENGINE=InnoDB COMMENT='质检记录';

-- -----------------------------------------------------------
-- 16. 退货单 (return_order)
-- -----------------------------------------------------------
CREATE TABLE return_order (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    return_no       VARCHAR(32)  NOT NULL UNIQUE,
    po_id           BIGINT       NOT NULL,
    arrival_id      BIGINT       NOT NULL,
    supplier_id     BIGINT       NOT NULL,
    reason          VARCHAR(256) NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / APPROVED / RETURNED / REJECTED',
    created_by      BIGINT       NOT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_po_id (po_id)
) ENGINE=InnoDB COMMENT='退货单';

-- -----------------------------------------------------------
-- 17. 退货行项 (return_line)
-- -----------------------------------------------------------
CREATE TABLE return_line (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    return_id       BIGINT       NOT NULL,
    material_id     BIGINT       NOT NULL,
    quantity        DECIMAL(12,2) NOT NULL,
    INDEX idx_return_id (return_id)
) ENGINE=InnoDB COMMENT='退货行项';

-- -----------------------------------------------------------
-- 18. 发票 (invoice)
-- -----------------------------------------------------------
CREATE TABLE invoice (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_no      VARCHAR(64)  NOT NULL,
    po_id           BIGINT       NOT NULL,
    supplier_id     BIGINT       NOT NULL,
    amount          DECIMAL(14,2) NOT NULL,
    tax_amount      DECIMAL(14,2) DEFAULT 0,
    invoice_date    DATE         NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'REGISTERED' COMMENT 'REGISTERED / VERIFIED / REJECTED',
    registered_by   BIGINT       NOT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_po_id (po_id),
    INDEX idx_supplier (supplier_id)
) ENGINE=InnoDB COMMENT='发票登记';

-- -----------------------------------------------------------
-- 19. 发票行项 (invoice_line)
-- -----------------------------------------------------------
CREATE TABLE invoice_line (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id      BIGINT       NOT NULL,
    po_line_id      BIGINT       NOT NULL,
    material_id     BIGINT       NOT NULL,
    quantity        DECIMAL(12,2) NOT NULL,
    unit_price      DECIMAL(12,4) NOT NULL,
    amount          DECIMAL(14,2) NOT NULL,
    INDEX idx_invoice_id (invoice_id)
) ENGINE=InnoDB COMMENT='发票行项';

-- -----------------------------------------------------------
-- 20. 对账单 (reconciliation)
-- -----------------------------------------------------------
CREATE TABLE reconciliation (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    recon_no        VARCHAR(32)  NOT NULL UNIQUE,
    po_id           BIGINT       NOT NULL,
    supplier_id     BIGINT       NOT NULL,
    order_amount    DECIMAL(14,2) NOT NULL COMMENT '订单金额',
    receipt_amount  DECIMAL(14,2) NOT NULL COMMENT '收货金额（按实收*单价）',
    invoice_amount  DECIMAL(14,2) NOT NULL COMMENT '发票金额',
    diff_amount     DECIMAL(14,2) GENERATED ALWAYS AS (invoice_amount - receipt_amount) STORED COMMENT '差异金额',
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / MATCHED / DIFFERENT / APPROVED / REJECTED',
    remark          VARCHAR(512),
    created_by      BIGINT       NOT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_po_id (po_id)
) ENGINE=InnoDB COMMENT='对账单';

-- -----------------------------------------------------------
-- 21. 对账行项 (reconciliation_line)
-- -----------------------------------------------------------
CREATE TABLE reconciliation_line (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    recon_id        BIGINT       NOT NULL,
    material_id     BIGINT       NOT NULL,
    po_line_id      BIGINT       NOT NULL,
    ordered_qty     DECIMAL(12,2),
    received_qty    DECIMAL(12,2),
    invoiced_qty    DECIMAL(12,2),
    unit_price      DECIMAL(12,4),
    order_amount    DECIMAL(14,2),
    receipt_amount  DECIMAL(14,2),
    invoice_amount  DECIMAL(14,2),
    diff_amount     DECIMAL(14,2),
    INDEX idx_recon_id (recon_id)
) ENGINE=InnoDB COMMENT='对账行项';

-- -----------------------------------------------------------
-- 22. 审批流 (approval)
-- -----------------------------------------------------------
CREATE TABLE approval (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    business_type   VARCHAR(32)  NOT NULL COMMENT 'PO / COMPARISON / RECONCILIATION / RETURN',
    business_id     BIGINT       NOT NULL,
    step            INT          NOT NULL DEFAULT 1,
    approver_id     BIGINT       NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / APPROVED / REJECTED',
    comment         VARCHAR(256),
    decided_at      DATETIME     NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_business (business_type, business_id),
    INDEX idx_approver (approver_id, status)
) ENGINE=InnoDB COMMENT='审批流';

-- -----------------------------------------------------------
-- 23. 操作审计日志 (audit_log)
-- -----------------------------------------------------------
CREATE TABLE audit_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT,
    username        VARCHAR(64),
    action          VARCHAR(64)  NOT NULL,
    entity_type     VARCHAR(64),
    entity_id       BIGINT,
    detail          TEXT         COMMENT 'JSON格式变更详情',
    ip_address      VARCHAR(64),
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_entity (entity_type, entity_id),
    INDEX idx_created (created_at)
) ENGINE=InnoDB COMMENT='操作审计日志';

-- -----------------------------------------------------------
-- 24. 定时提醒 (reminder)
-- -----------------------------------------------------------
CREATE TABLE reminder (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    type            VARCHAR(32)  NOT NULL COMMENT 'QUOTE_DEADLINE / PO_APPROVAL / ARRIVAL_OVERDUE / INVOICE_VERIFY',
    business_type   VARCHAR(32),
    business_id     BIGINT,
    target_user_id  BIGINT       NOT NULL,
    message         VARCHAR(256) NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / SENT / READ',
    trigger_time    DATETIME     NOT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    idempotency_key VARCHAR(128) UNIQUE COMMENT '幂等键 type:businessType:businessId',
    INDEX idx_target (target_user_id, status),
    INDEX idx_trigger (trigger_time, status)
) ENGINE=InnoDB COMMENT='定时提醒';
