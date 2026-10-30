-- ============================================================================
-- V5: Seed EOD Accrual Accounting Rules
-- Transaction Code: EOD_ACCRUAL
-- Double-Entry Equilibrium:
--   DR: 8011 (SYS_INT_EXPENSE - Chi phí trả lãi tiền gửi thanh toán CASA)
--   CR: 4911 (SYS_INT_PAYABLE - Lãi cộng dồn phải trả tiền gửi CASA)
-- ============================================================================

INSERT INTO ACCOUNTING_RULE_DEFINITION (
    rule_id, trx_code, entry_group, entry_index, account_role, abstract_acct_name, 
    debit_or_credit, amount_source, requires_fee, narration_template, effective_from
) VALUES (
    401, 'EOD_ACCRUAL', 1, 1, 'SYSTEM_GL', 'SYS_INT_EXPENSE', 
    'D', 'TRX_AMOUNT', 'N', 'Trich truoc chi phi lai tien gui CASA EOD', TO_DATE('2026-01-01', 'YYYY-MM-DD')
);

INSERT INTO ACCOUNTING_RULE_DEFINITION (
    rule_id, trx_code, entry_group, entry_index, account_role, abstract_acct_name, 
    debit_or_credit, amount_source, requires_fee, narration_template, effective_from
) VALUES (
    402, 'EOD_ACCRUAL', 1, 2, 'SYSTEM_GL', 'SYS_INT_PAYABLE', 
    'C', 'TRX_AMOUNT', 'N', 'Lai cong don phai tra tien gui CASA EOD', TO_DATE('2026-01-01', 'YYYY-MM-DD')
);
