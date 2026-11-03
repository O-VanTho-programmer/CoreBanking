-- ============================================================================
-- V8: Seed Lending CodeLists and Values
-- Standardizes Loan Account Status, Schedule Status, and Repayment Type
-- ============================================================================

-- 1. CODELIST GROUPS
INSERT INTO CODELIST (code_group, code_group_name, description, is_system, created_at)
VALUES ('LOAN_STATUS', 'Trang thai khoan vay tin dung', 'Trang thai hop dong vay cua khach hang', 'Y', SYSTIMESTAMP);

INSERT INTO CODELIST (code_group, code_group_name, description, is_system, created_at)
VALUES ('LOAN_SCHED_STATUS', 'Trang thai ky tra no khoan vay', 'Trang thai thanh toan tung ky trong lich tra no', 'Y', SYSTIMESTAMP);

INSERT INTO CODELIST (code_group, code_group_name, description, is_system, created_at)
VALUES ('REPAYMENT_TYPE', 'Phuong thuc tra no khoan vay', 'Phuong thuc tinh toan lich tra no khau hao', 'Y', SYSTIMESTAMP);


-- 2. CODELIST VALUES: LOAN_STATUS
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order, is_active)
VALUES ('LOAN_STATUS', 'APPROVED', 'Phe duyet cho giai ngan', 'Approved Pending Disbursement', 1, 'Y');

INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order, is_active)
VALUES ('LOAN_STATUS', 'ACTIVE', 'Dang hoat dong binh thuong', 'Active Normal', 2, 'Y');

INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order, is_active)
VALUES ('LOAN_STATUS', 'OVERDUE', 'Qua han thanh toan', 'Overdue', 3, 'Y');

INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order, is_active)
VALUES ('LOAN_STATUS', 'PAID_OFF', 'Da tat toan hoan tat', 'Fully Paid Off', 4, 'Y');

INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order, is_active)
VALUES ('LOAN_STATUS', 'BAD_DEBT', 'No xau noi bang', 'Bad Debt', 5, 'Y');


-- 3. CODELIST VALUES: LOAN_SCHED_STATUS
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order, is_active)
VALUES ('LOAN_SCHED_STATUS', 'PENDING', 'Chua den han thanh toan', 'Pending', 1, 'Y');

INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order, is_active)
VALUES ('LOAN_SCHED_STATUS', 'PARTIALLY_PAID', 'Da thanh toan mot phan', 'Partially Paid', 2, 'Y');

INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order, is_active)
VALUES ('LOAN_SCHED_STATUS', 'PAID', 'Da thanh toan du ky', 'Paid', 3, 'Y');

INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order, is_active)
VALUES ('LOAN_SCHED_STATUS', 'OVERDUE', 'Qua han ky thanh toan', 'Overdue', 4, 'Y');


-- 4. CODELIST VALUES: REPAYMENT_TYPE
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order, is_active)
VALUES ('REPAYMENT_TYPE', 'EQUAL_PRINCIPAL', 'Goc deu hang thang, lai tren du no giam dan', 'Equal Principal', 1, 'Y');

INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order, is_active)
VALUES ('REPAYMENT_TYPE', 'ANNUITY_EMI', 'Nien kim co dinh, tra gop deu hang thang', 'Annuity Equal Monthly Installment', 2, 'Y');
