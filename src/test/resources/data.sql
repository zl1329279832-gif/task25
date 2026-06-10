-- Test Data for Procurement Management System
-- Insert order matches schema.sql table definitions

-- Roles (same as schema.sql init data)
INSERT INTO sys_role (id, role_code, role_name, description) VALUES
(1, 'BUYER', '采购员', '创建询价、下单、查看'),
(2, 'PURCHASE_MANAGER', '采购主管', '审批、管理供应商和物料'),
(3, 'SUPPLIER', '供应商', '查看询价、提交报价、确认订单'),
(4, 'WAREHOUSE', '仓库人员', '收货、质检、退货'),
(5, 'FINANCE', '财务', '发票登记、审核、对账');

-- Users (password = admin123 for all)
INSERT INTO sys_user (id, username, password, real_name, phone, email, supplier_id, status) VALUES
(1, 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iKtMPqvHJ68P7Af5Y0zGODMmhGqq', '系统管理员', '13800000001', 'admin@procurement.com', NULL, 1),
(2, 'buyer01', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iKtMPqvHJ68P7Af5Y0zGODMmhGqq', '张采购', '13800000002', 'buyer01@procurement.com', NULL, 1),
(3, 'supplier01', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iKtMPqvHJ68P7Af5Y0zGODMmhGqq', '李供应商', '13800000003', 'supplier01@procurement.com', 1, 1),
(4, 'warehouse01', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iKtMPqvHJ68P7Af5Y0zGODMmhGqq', '王仓库', '13800000004', 'warehouse01@procurement.com', NULL, 1),
(5, 'finance01', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iKtMPqvHJ68P7Af5Y0zGODMmhGqq', '赵财务', '13800000005', 'finance01@procurement.com', NULL, 1);

-- User-Role
INSERT INTO sys_user_role (user_id, role_id) VALUES
(1, 2),
(2, 1),
(3, 3),
(4, 4),
(5, 5);

-- Suppliers
INSERT INTO supplier (id, supplier_code, supplier_name, contact_person, contact_phone, contact_email, address, qualification_status, bank_name, bank_account, rating) VALUES
(1, 'SUP-001', '优质钢材供应商', '李总', '13900000001', 'steel@supplier.com', '上海市浦东新区', 'QUALIFIED', '工商银行', '6222021234567890', 4.5),
(2, 'SUP-002', '精密零件供应商', '王经理', '13900000002', 'parts@supplier.com', '深圳市南山区', 'QUALIFIED', '建设银行', '6227001234567890', 4.0),
(3, 'SUP-003', '包装材料供应商', '赵主管', '13900000003', 'pack@supplier.com', '广州市天河区', 'PENDING', '中国银行', '6216001234567890', 3.5);

-- Material Categories
INSERT INTO material_category (id, parent_id, category_code, category_name, sort_order) VALUES
(1, 0, 'CAT-RAW', '原材料', 1),
(2, 0, 'CAT-PARTS', '零部件', 2),
(3, 1, 'CAT-STEEL', '钢材', 1),
(4, 1, 'CAT-PLASTIC', '塑料', 2),
(5, 2, 'CAT-BEARING', '轴承', 1);

-- Materials
INSERT INTO material (id, category_id, material_code, material_name, specification, unit, reference_price, description, status) VALUES
(1, 3, 'MAT-001', 'Q235钢板', '1000*2000*2mm', '张', 150.00, '普通碳素钢板', 1),
(2, 3, 'MAT-002', '304不锈钢管', 'DN50*3mm', '米', 85.00, '304不锈钢无缝管', 1),
(3, 5, 'MAT-003', '6205轴承', '25*52*15mm', '个', 12.50, 'NSK深沟球轴承', 1),
(4, 4, 'MAT-004', 'ABS塑料粒', '通用级', '千克', 15.00, '注塑级ABS', 1);
