-- ============================================================================
-- V7: Create Lending Module Schema
-- General Ledger additions, Credit Product Catalog, Loan Accounts & Schedules
-- ============================================================================

-- 1. SEED LENDING GENERAL LEDGER ACCOUNTS
INSERT INTO GL_ACCOUNT (gl_code, gl_name, account_type, normal_balance, parent_gl_code, gl_level, is_postable, currency, created_at)
VALUES ('2000', 'CHO VAY KHACH HANG', 'ASSET', 'D', NULL, 1, 'N', 'VND', SYSTIMESTAMP);

INSERT INTO GL_ACCOUNT (gl_code, gl_name, account_type, normal_balance, parent_gl_code, gl_level, is_postable, currency, created_at)
VALUES ('2111', 'Cho vay ngan han VND ca nhan', 'ASSET', 'D', '2000', 2, 'Y', 'VND', SYSTIMESTAMP);

INSERT INTO GL_ACCOUNT (gl_code, gl_name, account_type, normal_balance, parent_gl_code, gl_level, is_postable, currency, created_at)
VALUES ('2112', 'No qua han ngan han VND ca nhan', 'ASSET', 'D', '2000', 2, 'Y', 'VND', SYSTIMESTAMP);

INSERT INTO GL_ACCOUNT (gl_code, gl_name, account_type, normal_balance, parent_gl_code, gl_level, is_postable, currency, created_at)
VALUES ('3000', 'HOAT DONG THANH TOAN VA PHAI THU', 'ASSET', 'D', NULL, 1, 'N', 'VND', SYSTIMESTAMP);

INSERT INTO GL_ACCOUNT (gl_code, gl_name, account_type, normal_balance, parent_gl_code, gl_level, is_postable, currency, created_at)
VALUES ('3911', 'Lai cong don phai thu tu cho vay VND', 'ASSET', 'D', '3000', 2, 'Y', 'VND', SYSTIMESTAMP);

INSERT INTO GL_ACCOUNT (gl_code, gl_name, account_type, normal_balance, parent_gl_code, gl_level, is_postable, currency, created_at)
VALUES ('7020', 'Thu nhap lai cho vay VND', 'REVENUE', 'C', '7000', 2, 'Y', 'VND', SYSTIMESTAMP);


-- 2. CREDIT PRODUCT CATALOG
CREATE TABLE CREDIT_PRODUCT_CATALOG (
    product_code                VARCHAR2(20 CHAR)   NOT NULL,
    product_name                VARCHAR2(150 CHAR)  NOT NULL,
    currency                    CHAR(3)             DEFAULT 'VND' NOT NULL,
    principal_gl_code           VARCHAR2(20 CHAR)   NOT NULL,
    interest_revenue_gl_code    VARCHAR2(20 CHAR)   NOT NULL,
    interest_receivable_gl_code VARCHAR2(20 CHAR)   NOT NULL,
    overdue_principal_gl_code   VARCHAR2(20 CHAR)   NOT NULL,
    base_interest_rate          NUMBER(7,4)         NOT NULL,
    overdue_rate_multiplier     NUMBER(5,2)         DEFAULT 1.50 NOT NULL,
    day_count_basis             VARCHAR2(10 CHAR)   DEFAULT 'ACT_365' NOT NULL,
    min_principal_amount        NUMBER(18,4)        NOT NULL,
    max_principal_amount        NUMBER(18,4)        NOT NULL,
    min_term_months             NUMBER(4)           NOT NULL,
    max_term_months             NUMBER(4)           NOT NULL,
    default_repayment_type      VARCHAR2(20 CHAR)   NOT NULL,
    is_active                   CHAR(1)             DEFAULT 'Y' NOT NULL,
    created_at                  TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_credit_product PRIMARY KEY (product_code),
    CONSTRAINT fk_crd_gl_prin FOREIGN KEY (principal_gl_code) REFERENCES GL_ACCOUNT(gl_code),
    CONSTRAINT fk_crd_gl_int_rev FOREIGN KEY (interest_revenue_gl_code) REFERENCES GL_ACCOUNT(gl_code),
    CONSTRAINT fk_crd_gl_int_recv FOREIGN KEY (interest_receivable_gl_code) REFERENCES GL_ACCOUNT(gl_code),
    CONSTRAINT fk_crd_gl_overdue FOREIGN KEY (overdue_principal_gl_code) REFERENCES GL_ACCOUNT(gl_code),
    CONSTRAINT chk_crd_repay_type CHECK (default_repayment_type IN ('EQUAL_PRINCIPAL', 'ANNUITY_EMI')),
    CONSTRAINT chk_crd_day_count CHECK (day_count_basis IN ('ACT_365', 'ACT_360')),
    CONSTRAINT chk_crd_active CHECK (is_active IN ('Y', 'N'))
);

-- Seed Sample Credit Products
INSERT INTO CREDIT_PRODUCT_CATALOG (
    product_code, product_name, currency, principal_gl_code, interest_revenue_gl_code,
    interest_receivable_gl_code, overdue_principal_gl_code, base_interest_rate, overdue_rate_multiplier,
    day_count_basis, min_principal_amount, max_principal_amount, min_term_months, max_term_months,
    default_repayment_type, is_active, created_at
) VALUES (
    'LN_CONSUMER_VND', 'Vay tieu dung tin chap ca nhan VND', 'VND', '2111', '7020',
    '3911', '2112', 0.0950, 1.50,
    'ACT_365', 5000000.0000, 500000000.0000, 3, 60,
    'ANNUITY_EMI', 'Y', SYSTIMESTAMP
);

INSERT INTO CREDIT_PRODUCT_CATALOG (
    product_code, product_name, currency, principal_gl_code, interest_revenue_gl_code,
    interest_receivable_gl_code, overdue_principal_gl_code, base_interest_rate, overdue_rate_multiplier,
    day_count_basis, min_principal_amount, max_principal_amount, min_term_months, max_term_months,
    default_repayment_type, is_active, created_at
) VALUES (
    'LN_MORTGAGE_VND', 'Vay mua nha the chap bat dong san VND', 'VND', '2111', '7020',
    '3911', '2112', 0.0800, 1.50,
    'ACT_365', 100000000.0000, 10000000000.0000, 12, 300,
    'EQUAL_PRINCIPAL', 'Y', SYSTIMESTAMP
);


-- 3. LOAN ACCOUNT TABLE
CREATE TABLE LOAN_ACCOUNT (
    loan_id                     NUMBER(19)          NOT NULL,
    loan_account_number         VARCHAR2(34 CHAR)   NOT NULL,
    customer_id                 NUMBER(19)          NOT NULL,
    linked_casa_account_id      NUMBER(19)          NOT NULL,
    product_code                VARCHAR2(20 CHAR)   NOT NULL,
    principal_amount            NUMBER(18,4)        NOT NULL,
    remaining_principal         NUMBER(18,4)        NOT NULL,
    annual_interest_rate        NUMBER(7,4)         NOT NULL,
    overdue_interest_rate       NUMBER(7,4)         NOT NULL,
    term_months                 NUMBER(4)           NOT NULL,
    repayment_type              VARCHAR2(20 CHAR)   NOT NULL,
    day_count_basis             VARCHAR2(10 CHAR)   DEFAULT 'ACT_365' NOT NULL,
    loan_status                 VARCHAR2(20 CHAR)   DEFAULT 'APPROVED' NOT NULL,
    disbursed_at                TIMESTAMP WITH TIME ZONE,
    maturity_date               DATE,
    version                     NUMBER(19)          DEFAULT 0 NOT NULL,
    created_at                  TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at                  TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_loan_account PRIMARY KEY (loan_id),
    CONSTRAINT uq_loan_acct_num UNIQUE (loan_account_number),
    CONSTRAINT fk_loan_customer FOREIGN KEY (customer_id) REFERENCES CUSTOMER(customer_id),
    CONSTRAINT fk_loan_casa_acct FOREIGN KEY (linked_casa_account_id) REFERENCES ACCOUNT(account_id),
    CONSTRAINT fk_loan_product FOREIGN KEY (product_code) REFERENCES CREDIT_PRODUCT_CATALOG(product_code),
    CONSTRAINT chk_loan_status CHECK (loan_status IN ('APPROVED', 'ACTIVE', 'OVERDUE', 'PAID_OFF', 'BAD_DEBT')),
    CONSTRAINT chk_loan_repay_type CHECK (repayment_type IN ('EQUAL_PRINCIPAL', 'ANNUITY_EMI')),
    CONSTRAINT chk_loan_day_count CHECK (day_count_basis IN ('ACT_365', 'ACT_360'))
);


-- 4. LOAN SCHEDULE TABLE
CREATE TABLE LOAN_SCHEDULE (
    schedule_id                 NUMBER(19)          NOT NULL,
    loan_id                     NUMBER(19)          NOT NULL,
    period_number               NUMBER(4)           NOT NULL,
    due_date                    DATE                NOT NULL,
    principal_due               NUMBER(18,4)        NOT NULL,
    interest_due                NUMBER(18,4)        NOT NULL,
    total_installment           NUMBER(18,4)        NOT NULL,
    principal_paid              NUMBER(18,4)        DEFAULT 0 NOT NULL,
    interest_paid               NUMBER(18,4)        DEFAULT 0 NOT NULL,
    penalty_due                 NUMBER(18,4)        DEFAULT 0 NOT NULL,
    penalty_paid                NUMBER(18,4)        DEFAULT 0 NOT NULL,
    status                      VARCHAR2(20 CHAR)   DEFAULT 'PENDING' NOT NULL,
    created_at                  TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at                  TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_loan_schedule PRIMARY KEY (schedule_id),
    CONSTRAINT uq_loan_sched_period UNIQUE (loan_id, period_number),
    CONSTRAINT fk_loan_sched_loan FOREIGN KEY (loan_id) REFERENCES LOAN_ACCOUNT(loan_id),
    CONSTRAINT chk_sched_status CHECK (status IN ('PENDING', 'PARTIALLY_PAID', 'PAID', 'OVERDUE'))
);

CREATE INDEX idx_loan_sched_due ON LOAN_SCHEDULE (loan_id, due_date);
