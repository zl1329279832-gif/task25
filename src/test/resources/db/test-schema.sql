-- H2 兼容的测试 Schema (用于单元测试)
-- 简化版，仅包含核心表结构

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    real_name VARCHAR(64),
    phone VARCHAR(20),
    email VARCHAR(128),
    role VARCHAR(32) NOT NULL,
    supplier_id BIGINT,
    status INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS supplier (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    contact_person VARCHAR(64),
    phone VARCHAR(20),
    email VARCHAR(128),
    address VARCHAR(256),
    bank_name VARCHAR(128),
    bank_account VARCHAR(64),
    tax_number VARCHAR(64),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS material (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    category VARCHAR(64),
    unit VARCHAR(16) NOT NULL,
    spec VARCHAR(256),
    description VARCHAR(512),
    status INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS rfq (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rfq_no VARCHAR(32) NOT NULL UNIQUE,
    title VARCHAR(128),
    purchaser_id BIGINT NOT NULL,
    deadline TIMESTAMP NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS rfq_line (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rfq_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    quantity DECIMAL(12,2) NOT NULL,
    description VARCHAR(256),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS rfq_supplier (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rfq_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    invited_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS quote (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    quote_no VARCHAR(32) NOT NULL,
    rfq_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    version INT NOT NULL DEFAULT 1,
    total_amount DECIMAL(14,2),
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    frozen INT NOT NULL DEFAULT 0,
    submitted_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS quote_line (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    quote_id BIGINT NOT NULL,
    rfq_line_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    unit_price DECIMAL(12,4) NOT NULL,
    quantity DECIMAL(12,2) NOT NULL,
    delivery_days INT,
    remark VARCHAR(256)
);

CREATE TABLE IF NOT EXISTS comparison (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    comparison_no VARCHAR(32) NOT NULL UNIQUE,
    rfq_id BIGINT NOT NULL,
    rule VARCHAR(32) NOT NULL DEFAULT 'LOWEST_PRICE',
    result_summary TEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS comparison_line (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    comparison_id BIGINT NOT NULL,
    quote_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    total_amount DECIMAL(14,2),
    avg_delivery INT,
    score DECIMAL(6,2),
    rank_no INT,
    selected INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS purchase_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    po_no VARCHAR(32) NOT NULL UNIQUE,
    supplier_id BIGINT NOT NULL,
    comparison_id BIGINT,
    total_amount DECIMAL(14,2),
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    approved_by BIGINT,
    approved_at TIMESTAMP NULL,
    cancel_reason VARCHAR(256),
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS purchase_order_line (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    po_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    quantity DECIMAL(12,2) NOT NULL,
    received_qty DECIMAL(12,2) NOT NULL DEFAULT 0,
    unit_price DECIMAL(12,4) NOT NULL,
    amount DECIMAL(14,2)
);

CREATE TABLE IF NOT EXISTS arrival (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    arrival_no VARCHAR(32) NOT NULL UNIQUE,
    po_id BIGINT NOT NULL,
    batch_no INT NOT NULL DEFAULT 1,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    arrived_at TIMESTAMP NOT NULL,
    receiver_id BIGINT NOT NULL,
    remark VARCHAR(256),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS arrival_line (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    arrival_id BIGINT NOT NULL,
    po_line_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    ordered_qty DECIMAL(12,2) NOT NULL,
    arrived_qty DECIMAL(12,2) NOT NULL,
    accepted_qty DECIMAL(12,2) DEFAULT 0,
    diff_qty DECIMAL(12,2),
    diff_remark VARCHAR(256)
);

CREATE TABLE IF NOT EXISTS quality_inspection (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    inspection_no VARCHAR(32) NOT NULL UNIQUE,
    arrival_id BIGINT NOT NULL,
    inspector_id BIGINT NOT NULL,
    result VARCHAR(16) NOT NULL,
    remark VARCHAR(512),
    inspected_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS return_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    return_no VARCHAR(32) NOT NULL UNIQUE,
    po_id BIGINT NOT NULL,
    arrival_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    reason VARCHAR(256) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS return_line (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    return_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    quantity DECIMAL(12,2) NOT NULL
);

CREATE TABLE IF NOT EXISTS invoice (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_no VARCHAR(64) NOT NULL,
    po_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    amount DECIMAL(14,2) NOT NULL,
    tax_amount DECIMAL(14,2) DEFAULT 0,
    invoice_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'REGISTERED',
    registered_by BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS invoice_line (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    po_line_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    quantity DECIMAL(12,2) NOT NULL,
    unit_price DECIMAL(12,4) NOT NULL,
    amount DECIMAL(14,2) NOT NULL
);

CREATE TABLE IF NOT EXISTS reconciliation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recon_no VARCHAR(32) NOT NULL UNIQUE,
    po_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    order_amount DECIMAL(14,2) NOT NULL,
    receipt_amount DECIMAL(14,2) NOT NULL,
    invoice_amount DECIMAL(14,2) NOT NULL,
    diff_amount DECIMAL(14,2),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    remark VARCHAR(512),
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS reconciliation_line (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recon_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    po_line_id BIGINT NOT NULL,
    ordered_qty DECIMAL(12,2),
    received_qty DECIMAL(12,2),
    invoiced_qty DECIMAL(12,2),
    unit_price DECIMAL(12,4),
    order_amount DECIMAL(14,2),
    receipt_amount DECIMAL(14,2),
    invoice_amount DECIMAL(14,2),
    diff_amount DECIMAL(14,2)
);

CREATE TABLE IF NOT EXISTS approval (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    business_type VARCHAR(32) NOT NULL,
    business_id BIGINT NOT NULL,
    step INT NOT NULL DEFAULT 1,
    approver_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    comment VARCHAR(256),
    decided_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    username VARCHAR(64),
    action VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64),
    entity_id BIGINT,
    detail TEXT,
    ip_address VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS reminder (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    type VARCHAR(32) NOT NULL,
    business_type VARCHAR(32),
    business_id BIGINT,
    target_user_id BIGINT NOT NULL,
    message VARCHAR(256) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    trigger_time TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
