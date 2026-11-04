-- ============================================================================
-- V9: Loan Disbursement Rules and Journal Entry Loan Reference
-- Solution 1: Add loan_id reference to JOURNAL_ENTRY_ACTIVE and JOURNAL_ENTRY_DONE
-- Seed ACCOUNT_MAPPING for SYS_LOAN_PRINCIPAL and LOAN_DISBURSE Posting Rule
-- ============================================================================

-- 1. ADD LOAN_ID TO JOURNAL TABLES
ALTER TABLE JOURNAL_ENTRY_ACTIVE ADD (loan_id NUMBER(19));
ALTER TABLE JOURNAL_ENTRY_DONE ADD (loan_id NUMBER(19));

ALTER TABLE JOURNAL_ENTRY_ACTIVE 
    ADD CONSTRAINT fk_entry_act_loan FOREIGN KEY (loan_id) REFERENCES LOAN_ACCOUNT(loan_id);

CREATE INDEX idx_entry_act_loan ON JOURNAL_ENTRY_ACTIVE (loan_id);
CREATE INDEX idx_entry_done_loan ON JOURNAL_ENTRY_DONE (loan_id);


-- 2. SEED ACCOUNT MAPPING FOR LENDING ASSET (PRINCIPAL)
INSERT INTO ACCOUNT_MAPPING (mapping_id, mapping_name, account_type, description)
VALUES ('SYS_LOAN_PRINCIPAL', 'Du no goc cho vay ngan han VND', 'ASSET', 'Tai khoan phan anh du no goc giai ngan cho khach hang');

INSERT INTO ACCOUNT_MAPPING_DETAIL (detail_id, mapping_id, branch_code, currency, product_code, physical_gl_code)
VALUES (5, 'SYS_LOAN_PRINCIPAL', 'ALL', 'VND', 'ALL', '2111');


-- 3. SEED CODELIST FOR TRANSACTION CODE LOAN_DISBURSE
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order, is_active)
VALUES ('TRX_CODE', 'LOAN_DISBURSE', 'Giai ngan khoan vay tin dung', 'Loan Disbursement', 5, 'Y');


-- 4. SEED ACCOUNTING RULE DEFINITIONS FOR LOAN_DISBURSE
-- Leg 1: DR 2111 (Cho vay ngan han VND ca nhan - ASSET tang)
INSERT INTO ACCOUNTING_RULE_DEFINITION (
    rule_id, trx_code, entry_group, entry_index, account_role, abstract_acct_name, 
    debit_or_credit, amount_source, requires_fee, narration_template, effective_from
) VALUES (
    501, 'LOAN_DISBURSE', 1, 1, 'SYSTEM_GL', 'SYS_LOAN_PRINCIPAL', 
    'D', 'TRX_AMOUNT', 'N', 'Giai ngan khoan vay tin dung', TO_DATE('2026-01-01', 'YYYY-MM-DD')
);

-- Leg 2: CR 4211 (Tien gui thanh toan CASA khach hang - LIABILITY tang)
INSERT INTO ACCOUNTING_RULE_DEFINITION (
    rule_id, trx_code, entry_group, entry_index, account_role, abstract_acct_name, 
    debit_or_credit, amount_source, requires_fee, narration_template, effective_from
) VALUES (
    502, 'LOAN_DISBURSE', 1, 2, 'CUSTOMER_ACCOUNT', NULL, 
    'C', 'TRX_AMOUNT', 'N', 'Giai ngan vao tai khoan thanh toan CASA', TO_DATE('2026-01-01', 'YYYY-MM-DD')
);
