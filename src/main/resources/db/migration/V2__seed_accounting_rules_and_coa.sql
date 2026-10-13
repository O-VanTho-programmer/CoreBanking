-- ============================================================================
-- V2__seed_accounting_rules_and_coa.sql
-- Seed Core Banking Data:
-- 1. Universal CodeLists & Values (Replacing hardcoded enum checks)
-- 2. Operational Calendar & System Status
-- 3. Standard Chart of Accounts (COA)
-- 4. Abstract Account Mappings & Resolvers
-- 5. Deposit Product Catalog
-- 6. Governed Posting Rule Matrix
-- ============================================================================

-- ============================================================================
-- 1. SEED CODELISTS & VALUES
-- ============================================================================
-- ACCT_STATUS
INSERT INTO CODELIST (code_group, code_group_name, description) VALUES ('ACCT_STATUS', 'Trạng thái tài khoản', 'Trạng thái của tài khoản tiền gửi');
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order) VALUES ('ACCT_STATUS', 'ACTIVE', 'Hoạt động', 'Active', 1);
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order) VALUES ('ACCT_STATUS', 'DORMANT', 'Ngủ đông', 'Dormant', 2);
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order) VALUES ('ACCT_STATUS', 'BLOCKED', 'Phong tỏa', 'Blocked', 3);
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order) VALUES ('ACCT_STATUS', 'CLOSED', 'Đóng', 'Closed', 4);

-- CUST_STATUS
INSERT INTO CODELIST (code_group, code_group_name, description) VALUES ('CUST_STATUS', 'Trạng thái khách hàng', 'Trạng thái hồ sơ CIF');
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order) VALUES ('CUST_STATUS', 'ACTIVE', 'Hoạt động', 'Active', 1);
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order) VALUES ('CUST_STATUS', 'SUSPENDED', 'Tạm ngừng', 'Suspended', 2);
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order) VALUES ('CUST_STATUS', 'DECEASED', 'Đã mất', 'Deceased', 3);

-- KYC_STATUS
INSERT INTO CODELIST (code_group, code_group_name, description) VALUES ('KYC_STATUS', 'Trạng thái định danh', 'Trạng thái KYC của khách hàng');
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order) VALUES ('KYC_STATUS', 'PENDING', 'Chờ xác minh', 'Pending', 1);
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order) VALUES ('KYC_STATUS', 'VERIFIED', 'Đã xác minh', 'Verified', 2);
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order) VALUES ('KYC_STATUS', 'REJECTED', 'Từ chối', 'Rejected', 3);

-- CUST_TYPE
INSERT INTO CODELIST (code_group, code_group_name, description) VALUES ('CUST_TYPE', 'Loại khách hàng', 'Khách hàng cá nhân hoặc doanh nghiệp');
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order) VALUES ('CUST_TYPE', 'INDIVIDUAL', 'Cá nhân', 'Individual', 1);
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order) VALUES ('CUST_TYPE', 'CORPORATE', 'Doanh nghiệp', 'Corporate', 2);

-- HOLD_STATUS & HOLD_REASON
INSERT INTO CODELIST (code_group, code_group_name, description) VALUES ('HOLD_STATUS', 'Trạng thái giữ tiền', 'Trạng thái của lệnh phong tỏa/tạm giữ');
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order) VALUES ('HOLD_STATUS', 'ACTIVE', 'Đang giữ', 'Active', 1);
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order) VALUES ('HOLD_STATUS', 'RELEASED', 'Đã giải phóng', 'Released', 2);
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order) VALUES ('HOLD_STATUS', 'SETTLED', 'Đã quyết toán trừ tiền', 'Settled', 3);

INSERT INTO CODELIST (code_group, code_group_name, description) VALUES ('HOLD_REASON', 'Lý do giữ tiền', 'Lý do thực hiện phong tỏa/giữ tiền');
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order) VALUES ('HOLD_REASON', 'CARD_AUTH', 'Tạm giữ giao dịch thẻ', 'Card Authorization', 1);
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order) VALUES ('HOLD_REASON', 'LEGAL_FREEZE', 'Tòa án/Cơ quan yêu cầu phong tỏa', 'Legal Freeze', 2);
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order) VALUES ('HOLD_REASON', 'COLLATERAL_HOLD', 'Phong tỏa làm tài sản bảo đảm', 'Collateral Pledge', 3);

-- TRX_STATUS
INSERT INTO CODELIST (code_group, code_group_name, description) VALUES ('TRX_STATUS', 'Trạng thái giao dịch', 'Trạng thái của phiên giao dịch');
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order) VALUES ('TRX_STATUS', 'IN_FLIGHT', 'Đang xử lý', 'In Flight', 1);
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order) VALUES ('TRX_STATUS', 'PENDING_APPROVAL', 'Chờ duyệt (Maker-Checker)', 'Pending Approval', 2);
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order) VALUES ('TRX_STATUS', 'COMPLETED', 'Hoàn tất thành công', 'Completed', 3);
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order) VALUES ('TRX_STATUS', 'FAILED', 'Thất bại', 'Failed', 4);

-- CHANNEL
INSERT INTO CODELIST (code_group, code_group_name, description) VALUES ('CHANNEL', 'Kênh giao dịch', 'Kênh phát sinh giao dịch');
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order) VALUES ('CHANNEL', 'BRANCH', 'Tại quầy giao dịch', 'Branch Counter', 1);
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order) VALUES ('CHANNEL', 'MOBILE', 'Ngân hàng số Mobile', 'Mobile Banking', 2);
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order) VALUES ('CHANNEL', 'ATM', 'Cây rút tiền ATM/CDM', 'ATM Network', 3);
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order) VALUES ('CHANNEL', 'BATCH', 'Hệ thống chạy lô cuối ngày', 'EOD Batch Process', 4);

-- TRX_CODE
INSERT INTO CODELIST (code_group, code_group_name, description) VALUES ('TRX_CODE', 'Mã nghiệp vụ giao dịch', 'Mã giao dịch kế toán tài chính');
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order) VALUES ('TRX_CODE', 'CASH_DEP', 'Nộp tiền mặt', 'Cash Deposit', 1);
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order) VALUES ('TRX_CODE', 'CASH_WDR', 'Rút tiền mặt', 'Cash Withdrawal', 2);
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order) VALUES ('TRX_CODE', 'TRF_INTERNAL', 'Chuyển khoản nội bộ', 'Internal Transfer', 3);
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order) VALUES ('TRX_CODE', 'EOD_ACCRUAL', 'Trích lãi cuối ngày', 'Daily Interest Accrual', 4);

-- ACCOUNT_ROLE
INSERT INTO CODELIST (code_group, code_group_name, description) VALUES ('ACCOUNT_ROLE', 'Vai trò tài khoản trong định khoản', 'Nguồn tài khoản được lấy trong kịch bản định khoản');
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order) VALUES ('ACCOUNT_ROLE', 'CUSTOMER_ACCOUNT', 'Tài khoản nguồn khách hàng', 'Source Customer Account', 1);
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order) VALUES ('ACCOUNT_ROLE', 'TARGET_ACCOUNT', 'Tài khoản đích khách hàng', 'Target Customer Account', 2);
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order) VALUES ('ACCOUNT_ROLE', 'SYSTEM_GL', 'Tài khoản định danh hệ thống', 'System Abstract GL', 3);

-- AMOUNT_SOURCE
INSERT INTO CODELIST (code_group, code_group_name, description) VALUES ('AMOUNT_SOURCE', 'Nguồn số tiền định khoản', 'Trường dữ liệu mang giá trị số tiền');
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order) VALUES ('AMOUNT_SOURCE', 'TRX_AMOUNT', 'Số tiền giao dịch chính', 'Main Transaction Amount', 1);
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order) VALUES ('AMOUNT_SOURCE', 'FEE_AMOUNT', 'Số tiền phí dịch vụ', 'Service Fee Amount', 2);
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order) VALUES ('AMOUNT_SOURCE', 'TAX_AMOUNT', 'Số tiền thuế VAT', 'Tax / VAT Amount', 3);
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order) VALUES ('AMOUNT_SOURCE', 'PENALTY_AMOUNT', 'Số tiền phạt quá hạn', 'Penalty Amount', 4);

-- ============================================================================
-- 2. OPERATIONAL CALENDAR (System Working Date = 2026-10-13)
-- ============================================================================
INSERT INTO BANK_CALENDAR (calendar_date, working_date, is_working_day, fiscal_year, fiscal_period)
VALUES (TO_DATE('2026-10-13', 'YYYY-MM-DD'), TO_DATE('2026-10-13', 'YYYY-MM-DD'), 'Y', 2026, 10);

INSERT INTO SYSTEM_OPERATIONAL_STATUS (status_id, current_working_date, is_system_cutoff)
VALUES (1, TO_DATE('2026-10-13', 'YYYY-MM-DD'), 'N');

-- ============================================================================
-- 3. STANDARD CHART OF ACCOUNTS (COA)
-- ============================================================================
INSERT INTO GL_ACCOUNT (gl_code, gl_name, account_type, normal_balance, parent_gl_code, gl_level, is_postable, currency)
VALUES ('1000', 'TIEN MAT VA CAC KHOAN TUONG DUONG', 'ASSET', 'D', NULL, 1, 'N', 'VND');

INSERT INTO GL_ACCOUNT (gl_code, gl_name, account_type, normal_balance, parent_gl_code, gl_level, is_postable, currency)
VALUES ('1011', 'Tien mat tai quy chi nhanh VND', 'ASSET', 'D', '1000', 2, 'Y', 'VND');

INSERT INTO GL_ACCOUNT (gl_code, gl_name, account_type, normal_balance, parent_gl_code, gl_level, is_postable, currency)
VALUES ('4000', 'TIEN GUI CUA KHACH HANG', 'LIABILITY', 'C', NULL, 1, 'N', 'VND');

INSERT INTO GL_ACCOUNT (gl_code, gl_name, account_type, normal_balance, parent_gl_code, gl_level, is_postable, currency)
VALUES ('4211', 'Tien gui thanh toan ca nhan VND', 'LIABILITY', 'C', '4000', 2, 'Y', 'VND');

INSERT INTO GL_ACCOUNT (gl_code, gl_name, account_type, normal_balance, parent_gl_code, gl_level, is_postable, currency)
VALUES ('4911', 'Lai phai tra cho tien gui VND (Accrued Interest)', 'LIABILITY', 'C', '4000', 2, 'Y', 'VND');

INSERT INTO GL_ACCOUNT (gl_code, gl_name, account_type, normal_balance, parent_gl_code, gl_level, is_postable, currency)
VALUES ('7000', 'THU NHAP HOAT DONG DICH VU', 'REVENUE', 'C', NULL, 1, 'N', 'VND');

INSERT INTO GL_ACCOUNT (gl_code, gl_name, account_type, normal_balance, parent_gl_code, gl_level, is_postable, currency)
VALUES ('7111', 'Thu phi dich vu thanh toan trong nuoc', 'REVENUE', 'C', '7000', 2, 'Y', 'VND');

INSERT INTO GL_ACCOUNT (gl_code, gl_name, account_type, normal_balance, parent_gl_code, gl_level, is_postable, currency)
VALUES ('8000', 'CHI PHI HOAT DONG', 'EXPENSE', 'D', NULL, 1, 'N', 'VND');

INSERT INTO GL_ACCOUNT (gl_code, gl_name, account_type, normal_balance, parent_gl_code, gl_level, is_postable, currency)
VALUES ('8011', 'Chi phi tra lai tien gui thanh toan', 'EXPENSE', 'D', '8000', 2, 'Y', 'VND');

-- ============================================================================
-- 4. ACCOUNT ABSTRACTION LAYER (MAPPINGS)
-- ============================================================================
INSERT INTO ACCOUNT_MAPPING (mapping_id, mapping_name, account_type, description)
VALUES ('SYS_CASH_VAULT', 'Két tiền mặt chi nhánh', 'ASSET', 'Ket tien mat phuc vu giao dich nop rut tai quay');

INSERT INTO ACCOUNT_MAPPING (mapping_id, mapping_name, account_type, description)
VALUES ('SYS_FEE_INCOME', 'Thu nhập phí dịch vụ', 'REVENUE', 'Ghi nhan phi giao dich chuyen tien');

INSERT INTO ACCOUNT_MAPPING (mapping_id, mapping_name, account_type, description)
VALUES ('SYS_INT_EXPENSE', 'Chi phí trả lãi tiền gửi', 'EXPENSE', 'Chi phi lai tinh hang ngay cho khach hang');

INSERT INTO ACCOUNT_MAPPING (mapping_id, mapping_name, account_type, description)
VALUES ('SYS_INT_PAYABLE', 'Lãi cộng dồn phải trả', 'LIABILITY', 'Tich luy lai hang ngay chua nhap goc');

-- Detail Resolvers
INSERT INTO ACCOUNT_MAPPING_DETAIL (detail_id, mapping_id, branch_code, currency, product_code, physical_gl_code)
VALUES (1, 'SYS_CASH_VAULT', 'ALL', 'VND', 'ALL', '1011');

INSERT INTO ACCOUNT_MAPPING_DETAIL (detail_id, mapping_id, branch_code, currency, product_code, physical_gl_code)
VALUES (2, 'SYS_FEE_INCOME', 'ALL', 'VND', 'ALL', '7111');

INSERT INTO ACCOUNT_MAPPING_DETAIL (detail_id, mapping_id, branch_code, currency, product_code, physical_gl_code)
VALUES (3, 'SYS_INT_EXPENSE', 'ALL', 'VND', 'ALL', '8011');

INSERT INTO ACCOUNT_MAPPING_DETAIL (detail_id, mapping_id, branch_code, currency, product_code, physical_gl_code)
VALUES (4, 'SYS_INT_PAYABLE', 'ALL', 'VND', 'ALL', '4911');

-- ============================================================================
-- 5. PRODUCT CATALOG (CASA_VND)
-- ============================================================================
INSERT INTO PRODUCT_CATALOG (product_code, product_name, currency, default_gl_code, interest_gl_code, accrual_gl_code, annual_rate, day_count_basis, min_balance)
VALUES ('CASA_VND', 'Tai khoan thanh toan ca nhan VND', 'VND', '4211', '8011', '4911', 0.0050, 'ACT_365', 50000.0000);

-- ============================================================================
-- 6. GOVERNED POSTING RULE MATRIX
-- ============================================================================
-- Giao dịch 1: Nộp tiền mặt (CASH_DEP)
INSERT INTO ACCOUNTING_RULE_DEFINITION (
    rule_id, trx_code, entry_group, entry_index, account_role, abstract_acct_name, 
    debit_or_credit, amount_source, requires_fee, narration_template, effective_from
) VALUES (
    101, 'CASH_DEP', 1, 1, 'SYSTEM_GL', 'SYS_CASH_VAULT', 
    'D', 'TRX_AMOUNT', 'N', 'Nop tien mat vao tai khoan', TO_DATE('2026-01-01', 'YYYY-MM-DD')
);

INSERT INTO ACCOUNTING_RULE_DEFINITION (
    rule_id, trx_code, entry_group, entry_index, account_role, abstract_acct_name, 
    debit_or_credit, amount_source, requires_fee, narration_template, effective_from
) VALUES (
    102, 'CASH_DEP', 1, 2, 'CUSTOMER_ACCOUNT', NULL, 
    'C', 'TRX_AMOUNT', 'N', 'Nop tien mat vao tai khoan', TO_DATE('2026-01-01', 'YYYY-MM-DD')
);

-- Giao dịch 2: Rút tiền mặt (CASH_WDR)
INSERT INTO ACCOUNTING_RULE_DEFINITION (
    rule_id, trx_code, entry_group, entry_index, account_role, abstract_acct_name, 
    debit_or_credit, amount_source, requires_fee, narration_template, effective_from
) VALUES (
    201, 'CASH_WDR', 1, 1, 'CUSTOMER_ACCOUNT', NULL, 
    'D', 'TRX_AMOUNT', 'N', 'Rut tien mat tai quay', TO_DATE('2026-01-01', 'YYYY-MM-DD')
);

INSERT INTO ACCOUNTING_RULE_DEFINITION (
    rule_id, trx_code, entry_group, entry_index, account_role, abstract_acct_name, 
    debit_or_credit, amount_source, requires_fee, narration_template, effective_from
) VALUES (
    202, 'CASH_WDR', 1, 2, 'SYSTEM_GL', 'SYS_CASH_VAULT', 
    'C', 'TRX_AMOUNT', 'N', 'Rut tien mat tai quay', TO_DATE('2026-01-01', 'YYYY-MM-DD')
);

-- Giao dịch 3: Chuyển khoản nội bộ (TRF_INTERNAL)
INSERT INTO ACCOUNTING_RULE_DEFINITION (
    rule_id, trx_code, entry_group, entry_index, account_role, abstract_acct_name, 
    debit_or_credit, amount_source, requires_fee, narration_template, effective_from
) VALUES (
    301, 'TRF_INTERNAL', 1, 1, 'CUSTOMER_ACCOUNT', NULL, 
    'D', 'TRX_AMOUNT', 'N', 'Chuyen khoan di', TO_DATE('2026-01-01', 'YYYY-MM-DD')
);

INSERT INTO ACCOUNTING_RULE_DEFINITION (
    rule_id, trx_code, entry_group, entry_index, account_role, abstract_acct_name, 
    debit_or_credit, amount_source, requires_fee, narration_template, effective_from
) VALUES (
    302, 'TRF_INTERNAL', 1, 2, 'TARGET_ACCOUNT', NULL, 
    'C', 'TRX_AMOUNT', 'N', 'Nhan chuyen khoan den', TO_DATE('2026-01-01', 'YYYY-MM-DD')
);

INSERT INTO ACCOUNTING_RULE_DEFINITION (
    rule_id, trx_code, entry_group, entry_index, account_role, abstract_acct_name, 
    debit_or_credit, amount_source, requires_fee, narration_template, effective_from
) VALUES (
    303, 'TRF_INTERNAL', 2, 1, 'CUSTOMER_ACCOUNT', NULL, 
    'D', 'FEE_AMOUNT', 'Y', 'Thu phi chuyen khoan', TO_DATE('2026-01-01', 'YYYY-MM-DD')
);

INSERT INTO ACCOUNTING_RULE_DEFINITION (
    rule_id, trx_code, entry_group, entry_index, account_role, abstract_acct_name, 
    debit_or_credit, amount_source, requires_fee, narration_template, effective_from
) VALUES (
    304, 'TRF_INTERNAL', 2, 2, 'SYSTEM_GL', 'SYS_FEE_INCOME', 
    'C', 'FEE_AMOUNT', 'Y', 'Thu phi chuyen khoan', TO_DATE('2026-01-01', 'YYYY-MM-DD')
);
