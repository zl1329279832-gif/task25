-- =============================================
-- Procurement Management System - Database Schema
-- MySQL 8.0+
-- =============================================

CREATE DATABASE IF NOT EXISTS procurement DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE procurement;

-- ----------------------------
-- 1. sys_user
-- ----------------------------
CREATE TABLE sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(100) NOT NULL,
    real_name VARCHAR(50) NOT NULL,
    phone VARCHAR(20) DEFAULT NULL,
    email VARCHAR(100) DEFAULT NULL,
    supplier_id BIGINT DEFAULT NULL,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1=enabled 0=disabled',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='system user';

-- ----------------------------
-- 2. sys_role
-- ----------------------------
CREATE TABLE sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_code VARCHAR(30) NOT NULL,
    role_name VARCHAR(50) NOT NULL,
    description VARCHAR(200) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='role';

-- ----------------------------
-- 3. sys_user_role
-- ----------------------------
CREATE TABLE sys_user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    UNIQUE KEY uk_user_role (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user-role mapping';

-- ----------------------------
-- 4. supplier
-- ----------------------------
CREATE TABLE supplier (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    supplier_code VARCHAR(30) NOT NULL,
    supplier_name VARCHAR(100) NOT NULL,
    contact_person VARCHAR(50) DEFAULT NULL,
    contact_phone VARCHAR(20) DEFAULT NULL,
    contact_email VARCHAR(100) DEFAULT NULL,
    address VARCHAR(200) DEFAULT NULL,
    qualification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/QUALIFIED/DISQUALIFIED',
    qualification_expire_date DATE DEFAULT NULL,
    bank_name VARCHAR(100) DEFAULT NULL,
    bank_account VARCHAR(50) DEFAULT NULL,
    rating DECIMAL(3,1) DEFAULT 0.0,
    remark VARCHAR(500) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_supplier_code (supplier_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='supplier';

-- ----------------------------
-- 5. material_category
-- ----------------------------
CREATE TABLE material_category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_id BIGINT DEFAULT 0 COMMENT '0=top level',
    category_code VARCHAR(30) NOT NULL,
    category_name VARCHAR(50) NOT NULL,
    sort_order INT DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_category_code (category_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='material category';

-- ----------------------------
-- 6. material
-- ----------------------------
CREATE TABLE material (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_id BIGINT NOT NULL,
    material_code VARCHAR(30) NOT NULL,
    material_name VARCHAR(100) NOT NULL,
    specification VARCHAR(200) DEFAULT NULL,
    unit VARCHAR(20) NOT NULL,
    reference_price DECIMAL(12,2) DEFAULT NULL,
    description VARCHAR(500) DEFAULT NULL,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1=active 0=inactive',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_material_code (material_code),
    INDEX idx_category (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='material';

-- ----------------------------
-- 7. inquiry
-- ----------------------------
CREATE TABLE inquiry (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    inquiry_no VARCHAR(30) NOT NULL,
    title VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/QUOTING/CLOSED/CANCELLED',
    publish_time DATETIME DEFAULT NULL,
    deadline DATETIME DEFAULT NULL,
    close_time DATETIME DEFAULT NULL,
    buyer_id BIGINT NOT NULL,
    remark VARCHAR(500) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_inquiry_no (inquiry_no),
    INDEX idx_status (status),
    INDEX idx_buyer (buyer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='inquiry';

-- ----------------------------
-- 8. inquiry_item
-- ----------------------------
CREATE TABLE inquiry_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    inquiry_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    quantity DECIMAL(12,2) NOT NULL,
    expected_price DECIMAL(12,2) DEFAULT NULL,
    required_date DATE DEFAULT NULL,
    remark VARCHAR(200) DEFAULT NULL,
    INDEX idx_inquiry (inquiry_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='inquiry item';

-- ----------------------------
-- 9. inquiry_supplier
-- ----------------------------
CREATE TABLE inquiry_supplier (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    inquiry_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    UNIQUE KEY uk_inquiry_supplier (inquiry_id, supplier_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='inquiry invited suppliers';

-- ----------------------------
-- 10. quotation
-- ----------------------------
CREATE TABLE quotation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    quotation_no VARCHAR(30) NOT NULL,
    inquiry_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    version INT NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/SUBMITTED/FROZEN/SELECTED/REJECTED',
    total_amount DECIMAL(14,2) DEFAULT NULL,
    valid_until DATE DEFAULT NULL,
    submit_time DATETIME DEFAULT NULL,
    frozen_time DATETIME DEFAULT NULL,
    remark VARCHAR(500) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_quotation_no (quotation_no),
    INDEX idx_inquiry_supplier (inquiry_id, supplier_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='quotation';

-- ----------------------------
-- 11. quotation_item
-- ----------------------------
CREATE TABLE quotation_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    quotation_id BIGINT NOT NULL,
    inquiry_item_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    quantity DECIMAL(12,2) NOT NULL,
    amount DECIMAL(14,2) NOT NULL,
    delivery_days INT DEFAULT NULL,
    remark VARCHAR(200) DEFAULT NULL,
    INDEX idx_quotation (quotation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='quotation item';

-- ----------------------------
-- 12. comparison
-- ----------------------------
CREATE TABLE comparison (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    comparison_no VARCHAR(30) NOT NULL,
    inquiry_id BIGINT NOT NULL,
    comparison_type VARCHAR(20) NOT NULL COMMENT 'LOWEST_PRICE/COMPREHENSIVE',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/COMPLETED',
    result_summary TEXT DEFAULT NULL,
    operator_id BIGINT DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_comparison_no (comparison_no),
    INDEX idx_inquiry (inquiry_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='price comparison';

-- ----------------------------
-- 13. comparison_item
-- ----------------------------
CREATE TABLE comparison_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    comparison_id BIGINT NOT NULL,
    quotation_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    score DECIMAL(5,2) DEFAULT NULL,
    price_rank INT DEFAULT NULL,
    is_selected TINYINT DEFAULT 0,
    remark VARCHAR(200) DEFAULT NULL,
    INDEX idx_comparison (comparison_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='comparison item';

-- ----------------------------
-- 14. purchase_order
-- ----------------------------
CREATE TABLE purchase_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(30) NOT NULL,
    comparison_id BIGINT DEFAULT NULL,
    supplier_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_APPROVAL' COMMENT 'PENDING_APPROVAL/APPROVED/CONFIRMED/PARTIAL_DELIVERED/DELIVERED/COMPLETED/CANCELLED',
    total_amount DECIMAL(14,2) NOT NULL,
    paid_amount DECIMAL(14,2) DEFAULT 0,
    buyer_id BIGINT NOT NULL,
    expected_delivery_date DATE DEFAULT NULL,
    actual_delivery_date DATE DEFAULT NULL,
    confirm_time DATETIME DEFAULT NULL,
    approval_threshold VARCHAR(20) DEFAULT NULL,
    remark VARCHAR(500) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_order_no (order_no),
    INDEX idx_supplier (supplier_id),
    INDEX idx_status (status),
    INDEX idx_buyer (buyer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='purchase order';

-- ----------------------------
-- 15. order_item
-- ----------------------------
CREATE TABLE order_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    quantity DECIMAL(12,2) NOT NULL,
    received_quantity DECIMAL(12,2) DEFAULT 0,
    amount DECIMAL(14,2) NOT NULL,
    remark VARCHAR(200) DEFAULT NULL,
    INDEX idx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='order item';

-- ----------------------------
-- 16. delivery
-- ----------------------------
CREATE TABLE delivery (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    delivery_no VARCHAR(30) NOT NULL,
    order_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/INSPECTING/ACCEPTED/REJECTED/PARTIAL_ACCEPTED',
    delivery_date DATE NOT NULL,
    receiver_id BIGINT DEFAULT NULL,
    receive_time DATETIME DEFAULT NULL,
    remark VARCHAR(500) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_delivery_no (delivery_no),
    INDEX idx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='delivery';

-- ----------------------------
-- 17. delivery_item
-- ----------------------------
CREATE TABLE delivery_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    delivery_id BIGINT NOT NULL,
    order_item_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    expected_quantity DECIMAL(12,2) NOT NULL,
    actual_quantity DECIMAL(12,2) NOT NULL,
    remark VARCHAR(200) DEFAULT NULL,
    INDEX idx_delivery (delivery_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='delivery item';

-- ----------------------------
-- 18. delivery_diff
-- ----------------------------
CREATE TABLE delivery_diff (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    delivery_id BIGINT NOT NULL,
    delivery_item_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    diff_type VARCHAR(20) NOT NULL COMMENT 'SHORTAGE/EXCESS/DAMAGE',
    diff_quantity DECIMAL(12,2) NOT NULL,
    description VARCHAR(500) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_delivery (delivery_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='delivery difference';

-- ----------------------------
-- 19. inspection
-- ----------------------------
CREATE TABLE inspection (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    inspection_no VARCHAR(30) NOT NULL,
    delivery_id BIGINT NOT NULL,
    delivery_item_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    inspect_quantity DECIMAL(12,2) NOT NULL,
    qualified_quantity DECIMAL(12,2) NOT NULL,
    unqualified_quantity DECIMAL(12,2) DEFAULT 0,
    result VARCHAR(20) NOT NULL COMMENT 'QUALIFIED/UNQUALIFIED/CONCESSION_ACCEPT',
    inspector_id BIGINT NOT NULL,
    inspect_time DATETIME NOT NULL,
    remark VARCHAR(500) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_inspection_no (inspection_no),
    INDEX idx_delivery (delivery_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='inspection';

-- ----------------------------
-- 20. return_order
-- ----------------------------
CREATE TABLE return_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    return_no VARCHAR(30) NOT NULL,
    order_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    delivery_id BIGINT DEFAULT NULL,
    inspection_id BIGINT DEFAULT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SUPPLIER_CONFIRMED/RETURNING/COMPLETED',
    reason VARCHAR(500) NOT NULL,
    total_amount DECIMAL(14,2) DEFAULT NULL,
    applicant_id BIGINT NOT NULL,
    supplier_confirm_time DATETIME DEFAULT NULL,
    complete_time DATETIME DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_return_no (return_no),
    INDEX idx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='return order';

-- ----------------------------
-- 21. return_item
-- ----------------------------
CREATE TABLE return_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    return_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    return_quantity DECIMAL(12,2) NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    amount DECIMAL(14,2) NOT NULL,
    reason VARCHAR(200) DEFAULT NULL,
    INDEX idx_return (return_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='return item';

-- ----------------------------
-- 22. invoice
-- ----------------------------
CREATE TABLE invoice (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_no VARCHAR(50) NOT NULL,
    invoice_code VARCHAR(30) DEFAULT NULL,
    order_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'REGISTERED' COMMENT 'REGISTERED/VERIFIED/REJECTED',
    invoice_type VARCHAR(20) NOT NULL COMMENT 'NORMAL/SPECIAL',
    amount DECIMAL(14,2) NOT NULL,
    tax_amount DECIMAL(14,2) NOT NULL,
    total_amount DECIMAL(14,2) NOT NULL,
    invoice_date DATE NOT NULL,
    registrar_id BIGINT NOT NULL,
    verify_time DATETIME DEFAULT NULL,
    reject_reason VARCHAR(200) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_invoice_no (invoice_no),
    INDEX idx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='invoice';

-- ----------------------------
-- 23. invoice_item
-- ----------------------------
CREATE TABLE invoice_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    order_item_id BIGINT DEFAULT NULL,
    material_id BIGINT NOT NULL,
    quantity DECIMAL(12,2) NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    amount DECIMAL(14,2) NOT NULL,
    tax_rate DECIMAL(5,2) DEFAULT NULL,
    INDEX idx_invoice (invoice_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='invoice item';

-- ----------------------------
-- 24. reconciliation
-- ----------------------------
CREATE TABLE reconciliation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reconciliation_no VARCHAR(30) NOT NULL,
    supplier_id BIGINT NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/CONFIRMED/DISPUTED/SETTLED',
    order_amount DECIMAL(14,2) DEFAULT 0,
    delivery_amount DECIMAL(14,2) DEFAULT 0,
    invoice_amount DECIMAL(14,2) DEFAULT 0,
    return_amount DECIMAL(14,2) DEFAULT 0,
    net_amount DECIMAL(14,2) DEFAULT 0,
    diff_flag TINYINT DEFAULT 0,
    diff_description VARCHAR(500) DEFAULT NULL,
    confirm_time DATETIME DEFAULT NULL,
    operator_id BIGINT DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_reconciliation_no (reconciliation_no),
    INDEX idx_supplier_period (supplier_id, period_start, period_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='reconciliation';

-- ----------------------------
-- 25. reconciliation_item
-- ----------------------------
CREATE TABLE reconciliation_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reconciliation_id BIGINT NOT NULL,
    order_id BIGINT DEFAULT NULL,
    order_no VARCHAR(30) DEFAULT NULL,
    order_amount DECIMAL(14,2) DEFAULT NULL,
    delivered_amount DECIMAL(14,2) DEFAULT NULL,
    invoiced_amount DECIMAL(14,2) DEFAULT NULL,
    return_amount DECIMAL(14,2) DEFAULT 0,
    diff_amount DECIMAL(14,2) DEFAULT 0,
    diff_type VARCHAR(20) DEFAULT NULL COMMENT 'NONE/ORDER_DELIVERY/DELIVERY_INVOICE/ALL',
    remark VARCHAR(200) DEFAULT NULL,
    INDEX idx_reconciliation (reconciliation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='reconciliation item';

-- ----------------------------
-- 26. approval_record
-- ----------------------------
CREATE TABLE approval_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    business_type VARCHAR(30) NOT NULL,
    business_id BIGINT NOT NULL,
    business_no VARCHAR(30) DEFAULT NULL,
    approval_level INT NOT NULL,
    approver_id BIGINT NOT NULL,
    result VARCHAR(20) NOT NULL COMMENT 'APPROVED/REJECTED',
    opinion VARCHAR(500) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_business (business_type, business_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='approval record';

-- ----------------------------
-- 27. audit_log
-- ----------------------------
CREATE TABLE audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT DEFAULT NULL,
    username VARCHAR(50) DEFAULT NULL,
    module VARCHAR(30) NOT NULL,
    operation VARCHAR(30) NOT NULL COMMENT 'CREATE/UPDATE/DELETE/STATUS_CHANGE',
    business_type VARCHAR(30) DEFAULT NULL,
    business_id BIGINT DEFAULT NULL,
    detail TEXT DEFAULT NULL,
    ip_address VARCHAR(50) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_module_time (module, create_time),
    INDEX idx_user_time (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='audit log';

-- ----------------------------
-- Initial Data: Roles
-- ----------------------------
INSERT INTO sys_role (role_code, role_name, description) VALUES
('BUYER', '采购员', '创建询价、下单、查看'),
('PURCHASE_MANAGER', '采购主管', '审批、管理供应商和物料'),
('SUPPLIER', '供应商', '查看询价、提交报价、确认订单'),
('WAREHOUSE', '仓库人员', '收货、质检、退货'),
('FINANCE', '财务', '发票登记、审核、对账');

-- ----------------------------
-- Initial Data: Admin user (password: admin123)
-- ----------------------------
INSERT INTO sys_user (username, password, real_name, status) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iKtMPqvHJ68P7Af5Y0zGODMmhGqq', '系统管理员', 1);

INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 2);
