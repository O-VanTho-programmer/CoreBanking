-- ============================================================================
-- V10: Seed Loan Repayment, Aging, and Provisioning Rules
-- 1. Alter LOAN_ACCOUNT: add loan_group, days_past_due, specific_provision_amount, general_provision_amount
-- 2. Seed GL Accounts for Provisions and Provision Reversal Revenue
-- 3. Seed ACCOUNT_MAPPING and ACCOUNT_MAPPING_DETAIL
-- 4. Seed Transaction Codes (LOAN_REPAY, LOAN_OVERDUE_TRANSFER, LOAN_PROVISION, LOAN_PROVISION_RELEASE)
-- 5. Seed Accounting Rules for Repayment, Overdue Transfer, Provisioning, and Reversal
-- ============================================================================

-- 1. ALTER LOAN_ACCOUNT TO STORE HEALTH & PROVISION STATE
ALTER TABLE LOAN_ACCOUNT ADD (
    loan_group                  NUMBER(1)           DEFAULT 1 NOT NULL,
    days_past_due               NUMBER(5)           DEFAULT 0 NOT NULL,
    specific_provision_amount   NUMBER(18,4)        DEFAULT 0.0000 NOT NULL,
    general_provision_amount    NUMBER(18,4)        DEFAULT 0.0000 NOT NULL
);


-- 2. SEED GL ACCOUNTS
-- Quỹ dự phòng rủi ro tín dụng (ASSET - Contra, Normal Balance = C)
INSERT INTO GL_ACCOUNT (gl_code, gl_name, account_type, normal_balance, parent_gl_code, gl_level, is_postable, currency, created_at)
VALUES ('2191', 'Du phong chung rui ro tin dung VND', 'ASSET', 'C', '2000', 2, 'Y', 'VND', SYSTIMESTAMP);

INSERT INTO GL_ACCOUNT (gl_code, gl_name, account_type, normal_balance, parent_gl_code, gl_level, is_postable, currency, created_at)
VALUES ('2192', 'Du phong cu the rui ro tin dung VND', 'ASSET', 'C', '2000', 2, 'Y', 'VND', SYSTIMESTAMP);

-- Chi phí dự phòng rủi ro tín dụng (EXPENSE, Normal Balance = D)
INSERT INTO GL_ACCOUNT (gl_code, gl_name, account_type, normal_balance, parent_gl_code, gl_level, is_postable, currency, created_at)
VALUES ('8821', 'Chi phi du phong cu the rui ro tin dung VND', 'EXPENSE', 'D', '8000', 2, 'Y', 'VND', SYSTIMESTAMP);

INSERT INTO GL_ACCOUNT (gl_code, gl_name, account_type, normal_balance, parent_gl_code, gl_level, is_postable, currency, created_at)
VALUES ('8822', 'Chi phi du phong chung rui ro tin dung VND', 'EXPENSE', 'D', '8000', 2, 'Y', 'VND', SYSTIMESTAMP);

-- Thu nhập từ hoàn nhập dự phòng rủi ro tín dụng (REVENUE, Normal Balance = C)
INSERT INTO GL_ACCOUNT (gl_code, gl_name, account_type, normal_balance, parent_gl_code, gl_level, is_postable, currency, created_at)
VALUES ('7911', 'Thu tu hoan nhap du phong rui ro tin dung VND', 'REVENUE', 'C', '7000', 2, 'Y', 'VND', SYSTIMESTAMP);


-- 3. SEED ACCOUNT MAPPINGS & DETAILS
INSERT INTO ACCOUNT_MAPPING (mapping_id, mapping_name, account_type, description)
VALUES ('SYS_INT_REVENUE', 'Thu nhap lai cho vay VND', 'REVENUE', 'Ghi nhan lai vay thu tu khach hang');

INSERT INTO ACCOUNT_MAPPING (mapping_id, mapping_name, account_type, description)
VALUES ('SYS_LOAN_OVERDUE', 'No qua han ngan han VND', 'ASSET', 'Theo doi du no goc vay bi qua han');

INSERT INTO ACCOUNT_MAPPING (mapping_id, mapping_name, account_type, description)
VALUES ('SYS_PROV_GEN_EXP', 'Chi phi du phong chung', 'EXPENSE', 'Chi phi trich lap du phong chung 0.75%');

INSERT INTO ACCOUNT_MAPPING (mapping_id, mapping_name, account_type, description)
VALUES ('SYS_PROV_GEN_FUND', 'Quy du phong chung', 'ASSET', 'Quy du phong rui ro chung nhom 1-4');

INSERT INTO ACCOUNT_MAPPING (mapping_id, mapping_name, account_type, description)
VALUES ('SYS_PROV_SPEC_EXP', 'Chi phi du phong cu the', 'EXPENSE', 'Chi phi trich lap du phong cu the nhom 2-5');

INSERT INTO ACCOUNT_MAPPING (mapping_id, mapping_name, account_type, description)
VALUES ('SYS_PROV_SPEC_FUND', 'Quy du phong cu the', 'ASSET', 'Quy du phong rui ro cu the nhom 2-5');

INSERT INTO ACCOUNT_MAPPING (mapping_id, mapping_name, account_type, description)
VALUES ('PROV_DR_RVS', 'Giam quy du phong khi hoan nhap', 'ASSET', 'Giam so du quy du phong khi tat toan hoac nang hang no');

INSERT INTO ACCOUNT_MAPPING (mapping_id, mapping_name, account_type, description)
VALUES ('SYS_PROV_REVERSAL_INCOME', 'Thu nhap tu hoan nhap du phong', 'REVENUE', 'Ghi nhan doanh thu khi hoan nhap du phong');

-- Account Mapping Details
INSERT INTO ACCOUNT_MAPPING_DETAIL (detail_id, mapping_id, branch_code, currency, product_code, physical_gl_code)
VALUES (6, 'SYS_INT_REVENUE', 'ALL', 'VND', 'ALL', '7020');

INSERT INTO ACCOUNT_MAPPING_DETAIL (detail_id, mapping_id, branch_code, currency, product_code, physical_gl_code)
VALUES (7, 'SYS_LOAN_OVERDUE', 'ALL', 'VND', 'ALL', '2112');

INSERT INTO ACCOUNT_MAPPING_DETAIL (detail_id, mapping_id, branch_code, currency, product_code, physical_gl_code)
VALUES (8, 'SYS_PROV_GEN_EXP', 'ALL', 'VND', 'ALL', '8822');

INSERT INTO ACCOUNT_MAPPING_DETAIL (detail_id, mapping_id, branch_code, currency, product_code, physical_gl_code)
VALUES (9, 'SYS_PROV_GEN_FUND', 'ALL', 'VND', 'ALL', '2191');

INSERT INTO ACCOUNT_MAPPING_DETAIL (detail_id, mapping_id, branch_code, currency, product_code, physical_gl_code)
VALUES (10, 'SYS_PROV_SPEC_EXP', 'ALL', 'VND', 'ALL', '8821');

INSERT INTO ACCOUNT_MAPPING_DETAIL (detail_id, mapping_id, branch_code, currency, product_code, physical_gl_code)
VALUES (11, 'SYS_PROV_SPEC_FUND', 'ALL', 'VND', 'ALL', '2192');

INSERT INTO ACCOUNT_MAPPING_DETAIL (detail_id, mapping_id, branch_code, currency, product_code, physical_gl_code)
VALUES (12, 'PROV_DR_RVS', 'ALL', 'VND', 'ALL', '2192');

INSERT INTO ACCOUNT_MAPPING_DETAIL (detail_id, mapping_id, branch_code, currency, product_code, physical_gl_code)
VALUES (13, 'SYS_PROV_REVERSAL_INCOME', 'ALL', 'VND', 'ALL', '7911');


-- 4. SEED TRANSACTION CODES IN CODELIST_VALUE
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order, is_active)
VALUES ('TRX_CODE', 'LOAN_REPAY', 'Thu no khoan vay tin dung', 'Loan Repayment', 6, 'Y');

INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order, is_active)
VALUES ('TRX_CODE', 'LOAN_OVERDUE_TRANSFER', 'Chuyen no qua han', 'Loan Overdue Transfer', 7, 'Y');

INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order, is_active)
VALUES ('TRX_CODE', 'LOAN_PROVISION_GEN', 'Trich lap du phong chung', 'General Loan Provision', 8, 'Y');

INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order, is_active)
VALUES ('TRX_CODE', 'LOAN_PROVISION_SPEC', 'Trich lap du phong cu the', 'Specific Loan Provision', 9, 'Y');

INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order, is_active)
VALUES ('TRX_CODE', 'LOAN_PROVISION_RELEASE', 'Hoan nhap du phong cu the', 'Specific Provision Release', 10, 'Y');

INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order, is_active)
VALUES ('TRX_CODE', 'LOAN_PROV_GEN_RELEASE', 'Hoan nhap du phong chung', 'General Provision Release', 11, 'Y');


-- 5. SEED ACCOUNTING RULE DEFINITIONS
-- 5A. LOAN_REPAY
-- Group 1: Principal Repayment (DR CASA 4211, CR Loan Principal 2111)
INSERT INTO ACCOUNTING_RULE_DEFINITION (
    rule_id, trx_code, entry_group, entry_index, account_role, abstract_acct_name, 
    debit_or_credit, amount_source, requires_fee, narration_template, effective_from
) VALUES (
    601, 'LOAN_REPAY', 1, 1, 'CUSTOMER_ACCOUNT', NULL, 
    'D', 'PRINCIPAL_AMOUNT', 'N', 'Trich thu no goc tu tai khoan CASA', TO_DATE('2026-01-01', 'YYYY-MM-DD')
);

INSERT INTO ACCOUNTING_RULE_DEFINITION (
    rule_id, trx_code, entry_group, entry_index, account_role, abstract_acct_name, 
    debit_or_credit, amount_source, requires_fee, narration_template, effective_from
) VALUES (
    602, 'LOAN_REPAY', 1, 2, 'SYSTEM_GL', 'SYS_LOAN_PRINCIPAL', 
    'C', 'PRINCIPAL_AMOUNT', 'N', 'Thu no goc khoan vay tin dung', TO_DATE('2026-01-01', 'YYYY-MM-DD')
);

-- Group 2: Interest Repayment (DR CASA 4211, CR Interest Income 7020)
INSERT INTO ACCOUNTING_RULE_DEFINITION (
    rule_id, trx_code, entry_group, entry_index, account_role, abstract_acct_name, 
    debit_or_credit, amount_source, requires_fee, narration_template, effective_from
) VALUES (
    603, 'LOAN_REPAY', 2, 1, 'CUSTOMER_ACCOUNT', NULL, 
    'D', 'INTEREST_AMOUNT', 'N', 'Trich thu no lai tu tai khoan CASA', TO_DATE('2026-01-01', 'YYYY-MM-DD')
);

INSERT INTO ACCOUNTING_RULE_DEFINITION (
    rule_id, trx_code, entry_group, entry_index, account_role, abstract_acct_name, 
    debit_or_credit, amount_source, requires_fee, narration_template, effective_from
) VALUES (
    604, 'LOAN_REPAY', 2, 2, 'SYSTEM_GL', 'SYS_INT_REVENUE', 
    'C', 'INTEREST_AMOUNT', 'N', 'Thu no lai khoan vay tin dung', TO_DATE('2026-01-01', 'YYYY-MM-DD')
);

-- 5B. LOAN_OVERDUE_TRANSFER (DR 2112 Overdue / CR 2111 Normal)
INSERT INTO ACCOUNTING_RULE_DEFINITION (
    rule_id, trx_code, entry_group, entry_index, account_role, abstract_acct_name, 
    debit_or_credit, amount_source, requires_fee, narration_template, effective_from
) VALUES (
    611, 'LOAN_OVERDUE_TRANSFER', 1, 1, 'SYSTEM_GL', 'SYS_LOAN_OVERDUE', 
    'D', 'TRX_AMOUNT', 'N', 'Chuyen sang no qua han', TO_DATE('2026-01-01', 'YYYY-MM-DD')
);

INSERT INTO ACCOUNTING_RULE_DEFINITION (
    rule_id, trx_code, entry_group, entry_index, account_role, abstract_acct_name, 
    debit_or_credit, amount_source, requires_fee, narration_template, effective_from
) VALUES (
    612, 'LOAN_OVERDUE_TRANSFER', 1, 2, 'SYSTEM_GL', 'SYS_LOAN_PRINCIPAL', 
    'C', 'TRX_AMOUNT', 'N', 'Giam no goc trong han do qua han', TO_DATE('2026-01-01', 'YYYY-MM-DD')
);

-- 5C. LOAN_PROVISION_GEN (DR 8822 General Exp / CR 2191 General Fund)
INSERT INTO ACCOUNTING_RULE_DEFINITION (
    rule_id, trx_code, entry_group, entry_index, account_role, abstract_acct_name, 
    debit_or_credit, amount_source, requires_fee, narration_template, effective_from
) VALUES (
    621, 'LOAN_PROVISION_GEN', 1, 1, 'SYSTEM_GL', 'SYS_PROV_GEN_EXP', 
    'D', 'TRX_AMOUNT', 'N', 'Chi phi trich lap du phong chung EOD', TO_DATE('2026-01-01', 'YYYY-MM-DD')
);

INSERT INTO ACCOUNTING_RULE_DEFINITION (
    rule_id, trx_code, entry_group, entry_index, account_role, abstract_acct_name, 
    debit_or_credit, amount_source, requires_fee, narration_template, effective_from
) VALUES (
    622, 'LOAN_PROVISION_GEN', 1, 2, 'SYSTEM_GL', 'SYS_PROV_GEN_FUND', 
    'C', 'TRX_AMOUNT', 'N', 'Quy du phong chung rui ro tin dung EOD', TO_DATE('2026-01-01', 'YYYY-MM-DD')
);

-- 5D. LOAN_PROVISION_SPEC (DR 8821 Specific Exp / CR 2192 Specific Fund)
INSERT INTO ACCOUNTING_RULE_DEFINITION (
    rule_id, trx_code, entry_group, entry_index, account_role, abstract_acct_name, 
    debit_or_credit, amount_source, requires_fee, narration_template, effective_from
) VALUES (
    623, 'LOAN_PROVISION_SPEC', 1, 1, 'SYSTEM_GL', 'SYS_PROV_SPEC_EXP', 
    'D', 'TRX_AMOUNT', 'N', 'Chi phi trich lap du phong cu the EOD', TO_DATE('2026-01-01', 'YYYY-MM-DD')
);

INSERT INTO ACCOUNTING_RULE_DEFINITION (
    rule_id, trx_code, entry_group, entry_index, account_role, abstract_acct_name, 
    debit_or_credit, amount_source, requires_fee, narration_template, effective_from
) VALUES (
    624, 'LOAN_PROVISION_SPEC', 1, 2, 'SYSTEM_GL', 'SYS_PROV_SPEC_FUND', 
    'C', 'TRX_AMOUNT', 'N', 'Quy du phong cu the rui ro tin dung EOD', TO_DATE('2026-01-01', 'YYYY-MM-DD')
);

-- 5E. LOAN_PROVISION_RELEASE (DR 2192 Specific Fund / CR 7911 Reversal Income)
INSERT INTO ACCOUNTING_RULE_DEFINITION (
    rule_id, trx_code, entry_group, entry_index, account_role, abstract_acct_name, 
    debit_or_credit, amount_source, requires_fee, narration_template, effective_from
) VALUES (
    631, 'LOAN_PROVISION_RELEASE', 1, 1, 'SYSTEM_GL', 'PROV_DR_RVS', 
    'D', 'TRX_AMOUNT', 'N', 'Hoan nhap quy du phong cu the khi tat toan', TO_DATE('2026-01-01', 'YYYY-MM-DD')
);

INSERT INTO ACCOUNTING_RULE_DEFINITION (
    rule_id, trx_code, entry_group, entry_index, account_role, abstract_acct_name, 
    debit_or_credit, amount_source, requires_fee, narration_template, effective_from
) VALUES (
    632, 'LOAN_PROVISION_RELEASE', 1, 2, 'SYSTEM_GL', 'SYS_PROV_REVERSAL_INCOME', 
    'C', 'TRX_AMOUNT', 'N', 'Thu nhap tu hoan nhap du phong cu the', TO_DATE('2026-01-01', 'YYYY-MM-DD')
);

-- 5F. LOAN_PROV_GEN_RELEASE (DR 2191 General Fund / CR 7911 Reversal Income)
INSERT INTO ACCOUNTING_RULE_DEFINITION (
    rule_id, trx_code, entry_group, entry_index, account_role, abstract_acct_name, 
    debit_or_credit, amount_source, requires_fee, narration_template, effective_from
) VALUES (
    633, 'LOAN_PROV_GEN_RELEASE', 1, 1, 'SYSTEM_GL', 'SYS_PROV_GEN_FUND', 
    'D', 'TRX_AMOUNT', 'N', 'Hoan nhap quy du phong chung khi tat toan', TO_DATE('2026-01-01', 'YYYY-MM-DD')
);

INSERT INTO ACCOUNTING_RULE_DEFINITION (
    rule_id, trx_code, entry_group, entry_index, account_role, abstract_acct_name, 
    debit_or_credit, amount_source, requires_fee, narration_template, effective_from
) VALUES (
    634, 'LOAN_PROV_GEN_RELEASE', 1, 2, 'SYSTEM_GL', 'SYS_PROV_REVERSAL_INCOME', 
    'C', 'TRX_AMOUNT', 'N', 'Thu nhap tu hoan nhap du phong chung', TO_DATE('2026-01-01', 'YYYY-MM-DD')
);
