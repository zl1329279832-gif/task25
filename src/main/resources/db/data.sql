-- ============================================================
-- 测试数据
-- ============================================================

USE procurement;

-- 供应商
INSERT INTO supplier (code, name, contact_person, phone, email, address, status) VALUES
('SUP-001', '华东钢铁有限公司', '张经理', '13800001111', 'zhang@huadong.com', '上海市浦东新区', 'ACTIVE'),
('SUP-002', '南方电子元器件', '李总', '13800002222', 'li@south.com', '深圳市南山区', 'ACTIVE'),
('SUP-003', '北方化工集团', '王主管', '13800003333', 'wang@north.com', '天津市滨海新区', 'ACTIVE');

-- 物料
INSERT INTO material (code, name, category, unit, spec) VALUES
('MAT-001', 'Q235钢板', '钢材', '吨', '10mm*1500mm*6000mm'),
('MAT-002', '电阻10KΩ', '电子元件', '个', '0805贴片'),
('MAT-003', '工业盐酸', '化工品', '升', '浓度36%'),
('MAT-004', '不锈钢管304', '钢材', '米', 'DN50*3mm'),
('MAT-005', '电容100μF', '电子元件', '个', '16V贴片');

-- 用户 (密码均为 password123 的 BCrypt 加密)
INSERT INTO sys_user (username, password, real_name, phone, role, supplier_id) VALUES
('purchaser01', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '赵采购', '13900001111', 'PURCHASER', NULL),
('manager01',   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '钱主管', '13900002222', 'PURCHASE_MANAGER', NULL),
('supplier01',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '华东钢铁-系统员', '13900003333', 'SUPPLIER', 1),
('supplier02',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '南方电子-系统员', '13900004444', 'SUPPLIER', 2),
('warehouse01', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '孙仓管', '13900005555', 'WAREHOUSE', NULL),
('finance01',   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '周财务', '13900006666', 'FINANCE', NULL);

-- 询价单
INSERT INTO rfq (rfq_no, title, purchaser_id, deadline, status) VALUES
('RFQ-2026-0001', '2026年Q2钢材集中采购询价', 1, '2026-06-20 18:00:00', 'PUBLISHED'),
('RFQ-2026-0002', '电子元件紧急补货询价', 1, '2026-06-15 12:00:00', 'PUBLISHED');

INSERT INTO rfq_line (rfq_id, material_id, quantity) VALUES
(1, 1, 100.00),
(1, 4, 500.00),
(2, 2, 10000.00),
(2, 5, 5000.00);

INSERT INTO rfq_supplier (rfq_id, supplier_id) VALUES
(1, 1), (1, 3),
(2, 2);

-- 报价（华东钢铁 对 RFQ-001 的 v1 报价）
INSERT INTO quote (quote_no, rfq_id, supplier_id, version, total_amount, status, frozen) VALUES
('QT-2026-0001', 1, 1, 1, 485000.00, 'SUBMITTED', 0);

INSERT INTO quote_line (quote_id, rfq_line_id, material_id, unit_price, quantity, delivery_days) VALUES
(1, 1, 1, 4200.00, 100.00, 15),
(1, 2, 4, 130.00, 500.00, 10);

-- 报价 v2（华东钢铁改价）
INSERT INTO quote (quote_no, rfq_id, supplier_id, version, total_amount, status, frozen) VALUES
('QT-2026-0001', 1, 1, 2, 475000.00, 'SUBMITTED', 0);

INSERT INTO quote_line (quote_id, rfq_line_id, material_id, unit_price, quantity, delivery_days) VALUES
(2, 1, 1, 4100.00, 100.00, 12),
(2, 2, 4, 130.00, 500.00, 10);

-- 北方化工 对 RFQ-001 的报价
INSERT INTO quote (quote_no, rfq_id, supplier_id, version, total_amount, status, frozen) VALUES
('QT-2026-0002', 1, 3, 1, 500000.00, 'SUBMITTED', 0);

INSERT INTO quote_line (quote_id, rfq_line_id, material_id, unit_price, quantity, delivery_days) VALUES
(3, 1, 1, 4350.00, 100.00, 20),
(3, 2, 4, 130.00, 500.00, 14);
