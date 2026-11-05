-- ============================================================================
-- V11: Implement Pluggable Multi-Charge IFC Pricing Engine & Loan Balance Bucket
-- 1. Alter CREDIT_PRODUCT_CATALOG: add default_margin_rate, min_margin_rate, max_margin_rate
-- 2. Alter LOAN_ACCOUNT: add interest_margin
-- 3. Create LOAN_BALANCE_BUCKET for historical lending snapshots in EOD
-- 4. Create DEPOSIT IFC Tables: DEPOSIT_IFC_DEFINITION, DEPOSIT_PRODUCT_IFC_MAPPING, ACCOUNT_IFC
-- 5. Create CREDIT IFC Tables: CREDIT_IFC_DEFINITION, CREDIT_PRODUCT_IFC_MAPPING, LOAN_ACCOUNT_IFC
-- 6. Seed IFC Definitions and Product Mappings for CASA and Loans
-- ============================================================================

-- 1. ALTER CREDIT_PRODUCT_CATALOG TO SUPPORT TWO-TIER MARGIN INTEREST
ALTER TABLE CREDIT_PRODUCT_CATALOG ADD (
    default_margin_rate         NUMBER(7,4)         DEFAULT 0.0000 NOT NULL,
    min_margin_rate             NUMBER(7,4)         DEFAULT 0.0000 NOT NULL,
    max_margin_rate             NUMBER(7,4)         DEFAULT 0.1000 NOT NULL
);

UPDATE CREDIT_PRODUCT_CATALOG 
SET default_margin_rate = 0.0250, min_margin_rate = 0.0100, max_margin_rate = 0.0500 
WHERE product_code = 'LN_CONSUMER_VND';

UPDATE CREDIT_PRODUCT_CATALOG 
SET default_margin_rate = 0.0350, min_margin_rate = 0.0200, max_margin_rate = 0.0600 
WHERE product_code = 'LN_MORTGAGE_VND';


-- 2. ALTER LOAN_ACCOUNT TO STORE CONTRACT-SPECIFIC INTEREST MARGIN
ALTER TABLE LOAN_ACCOUNT ADD (
    interest_margin             NUMBER(7,4)         DEFAULT 0.0000 NOT NULL
);


-- 3. CREATE LOAN_BALANCE_BUCKET (IMMUTABLE EOD CREDIT SNAPSHOTS)
CREATE TABLE LOAN_BALANCE_BUCKET (
    loan_id                     NUMBER(19)          NOT NULL,
    working_date                DATE                NOT NULL,
    outstanding_principal       NUMBER(18,4)        NOT NULL,
    past_due_principal          NUMBER(18,4)        DEFAULT 0.0000 NOT NULL,
    in_term_principal           NUMBER(18,4)        DEFAULT 0.0000 NOT NULL,
    accrued_interest            NUMBER(18,4)        DEFAULT 0.0000 NOT NULL,
    overdue_interest            NUMBER(18,4)        DEFAULT 0.0000 NOT NULL,
    penalty_due                 NUMBER(18,4)        DEFAULT 0.0000 NOT NULL,
    loan_group                  NUMBER(1)           NOT NULL,
    days_past_due               NUMBER(5)           NOT NULL,
    general_provision           NUMBER(18,4)        DEFAULT 0.0000 NOT NULL,
    specific_provision          NUMBER(18,4)        DEFAULT 0.0000 NOT NULL,
    created_at                  TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_loan_bal_bucket PRIMARY KEY (loan_id, working_date),
    CONSTRAINT fk_loan_bucket_loan FOREIGN KEY (loan_id) REFERENCES LOAN_ACCOUNT(loan_id)
);


-- 4. DEPOSIT IFC PRICING TABLES
CREATE TABLE DEPOSIT_IFC_DEFINITION (
    ifc_code                    VARCHAR2(30 CHAR)   NOT NULL,
    ifc_name                    VARCHAR2(100 CHAR)  NOT NULL,
    ifc_type                    CHAR(1)             NOT NULL, -- 'I' = Interest (Expense), 'F' = Fee (Income)
    calculation_method          VARCHAR2(20 CHAR)   NOT NULL, -- PERCENTAGE, FLAT, SLAB
    frequency                   VARCHAR2(20 CHAR)   NOT NULL, -- DAILY, MONTHLY, ONETIME
    gl_income_mapping           VARCHAR2(50 CHAR),
    gl_expense_mapping          VARCHAR2(50 CHAR),
    gl_payable_mapping          VARCHAR2(50 CHAR),
    created_at                  TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_dpt_ifc_def PRIMARY KEY (ifc_code),
    CONSTRAINT chk_dpt_ifc_type CHECK (ifc_type IN ('I', 'F')),
    CONSTRAINT chk_dpt_ifc_freq CHECK (frequency IN ('DAILY', 'MONTHLY', 'ONETIME'))
);

CREATE TABLE DEPOSIT_PRODUCT_IFC_MAPPING (
    mapping_id                  NUMBER(19)          NOT NULL,
    product_code                VARCHAR2(20 CHAR)   NOT NULL,
    ifc_code                    VARCHAR2(30 CHAR)   NOT NULL,
    default_rate                NUMBER(18,4)        NOT NULL,
    min_rate                    NUMBER(18,4)        NOT NULL,
    max_rate                    NUMBER(18,4)        NOT NULL,
    is_mandatory                CHAR(1)             DEFAULT 'Y' NOT NULL,
    created_at                  TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_dpt_prod_ifc_map PRIMARY KEY (mapping_id),
    CONSTRAINT fk_dpt_map_prod FOREIGN KEY (product_code) REFERENCES DPT_PRODUCT_CATALOG(product_code),
    CONSTRAINT fk_dpt_map_ifc FOREIGN KEY (ifc_code) REFERENCES DEPOSIT_IFC_DEFINITION(ifc_code),
    CONSTRAINT uq_dpt_prod_ifc UNIQUE (product_code, ifc_code),
    CONSTRAINT chk_dpt_map_mand CHECK (is_mandatory IN ('Y', 'N'))
);

CREATE TABLE ACCOUNT_IFC (
    account_ifc_id              NUMBER(19)          NOT NULL,
    account_id                  NUMBER(19)          NOT NULL,
    ifc_code                    VARCHAR2(30 CHAR)   NOT NULL,
    applied_rate                NUMBER(18,4)        NOT NULL,
    is_waived                   CHAR(1)             DEFAULT 'N' NOT NULL,
    effective_from              DATE                NOT NULL,
    effective_to                DATE,
    created_at                  TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_account_ifc PRIMARY KEY (account_ifc_id),
    CONSTRAINT fk_acct_ifc_acct FOREIGN KEY (account_id) REFERENCES ACCOUNT(account_id),
    CONSTRAINT fk_acct_ifc_code FOREIGN KEY (ifc_code) REFERENCES DEPOSIT_IFC_DEFINITION(ifc_code),
    CONSTRAINT uq_acct_ifc UNIQUE (account_id, ifc_code),
    CONSTRAINT chk_acct_ifc_waived CHECK (is_waived IN ('Y', 'N'))
);


-- 5. CREDIT IFC PRICING TABLES
CREATE TABLE CREDIT_IFC_DEFINITION (
    ifc_code                    VARCHAR2(30 CHAR)   NOT NULL,
    ifc_name                    VARCHAR2(100 CHAR)  NOT NULL,
    ifc_type                    CHAR(1)             NOT NULL, -- 'I' = Interest (Revenue), 'F' = Fee (Income)
    calculation_method          VARCHAR2(20 CHAR)   NOT NULL, -- PERCENTAGE, FLAT, SLAB
    frequency                   VARCHAR2(20 CHAR)   NOT NULL, -- DAILY, MONTHLY, ONETIME
    gl_income_mapping           VARCHAR2(50 CHAR),
    gl_receivable_mapping       VARCHAR2(50 CHAR),
    created_at                  TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_crd_ifc_def PRIMARY KEY (ifc_code),
    CONSTRAINT chk_crd_ifc_type CHECK (ifc_type IN ('I', 'F')),
    CONSTRAINT chk_crd_ifc_freq CHECK (frequency IN ('DAILY', 'MONTHLY', 'ONETIME'))
);

CREATE TABLE CREDIT_PRODUCT_IFC_MAPPING (
    mapping_id                  NUMBER(19)          NOT NULL,
    product_code                VARCHAR2(20 CHAR)   NOT NULL,
    ifc_code                    VARCHAR2(30 CHAR)   NOT NULL,
    default_rate                NUMBER(18,4)        NOT NULL,
    min_rate                    NUMBER(18,4)        NOT NULL,
    max_rate                    NUMBER(18,4)        NOT NULL,
    is_mandatory                CHAR(1)             DEFAULT 'Y' NOT NULL,
    created_at                  TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_crd_prod_ifc_map PRIMARY KEY (mapping_id),
    CONSTRAINT fk_crd_map_prod FOREIGN KEY (product_code) REFERENCES CREDIT_PRODUCT_CATALOG(product_code),
    CONSTRAINT fk_crd_map_ifc FOREIGN KEY (ifc_code) REFERENCES CREDIT_IFC_DEFINITION(ifc_code),
    CONSTRAINT uq_crd_prod_ifc UNIQUE (product_code, ifc_code),
    CONSTRAINT chk_crd_map_mand CHECK (is_mandatory IN ('Y', 'N'))
);

CREATE TABLE LOAN_ACCOUNT_IFC (
    contract_ifc_id             NUMBER(19)          NOT NULL,
    loan_id                     NUMBER(19)          NOT NULL,
    ifc_code                    VARCHAR2(30 CHAR)   NOT NULL,
    applied_rate                NUMBER(18,4)        NOT NULL,
    margin_rate                 NUMBER(7,4)         DEFAULT 0.0000 NOT NULL,
    is_waived                   CHAR(1)             DEFAULT 'N' NOT NULL,
    created_at                  TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_loan_acct_ifc PRIMARY KEY (contract_ifc_id),
    CONSTRAINT fk_loan_ifc_loan FOREIGN KEY (loan_id) REFERENCES LOAN_ACCOUNT(loan_id),
    CONSTRAINT fk_loan_ifc_code FOREIGN KEY (ifc_code) REFERENCES CREDIT_IFC_DEFINITION(ifc_code),
    CONSTRAINT uq_loan_acct_ifc UNIQUE (loan_id, ifc_code),
    CONSTRAINT chk_loan_ifc_waived CHECK (is_waived IN ('Y', 'N'))
);


-- 6. SEED IFC DEFINITIONS & PRODUCT MAPPINGS
-- 6A. Deposit IFCs
INSERT INTO DEPOSIT_IFC_DEFINITION (ifc_code, ifc_name, ifc_type, calculation_method, frequency, gl_expense_mapping, gl_payable_mapping, created_at)
VALUES ('INT_SAVINGS_DEMAND', 'Lai tien gui khong ky han CASA', 'I', 'PERCENTAGE', 'DAILY', 'SYS_INTEREST_EXPENSE', 'SYS_ACCRUED_INTEREST_PAYABLE', SYSTIMESTAMP);

INSERT INTO DEPOSIT_IFC_DEFINITION (ifc_code, ifc_name, ifc_type, calculation_method, frequency, gl_income_mapping, created_at)
VALUES ('FEE_MONTHLY_MAINT', 'Phi quan ly duy tri tai khoan thang', 'F', 'FLAT', 'MONTHLY', 'SYS_FEE_INCOME', SYSTIMESTAMP);

INSERT INTO DEPOSIT_IFC_DEFINITION (ifc_code, ifc_name, ifc_type, calculation_method, frequency, gl_income_mapping, created_at)
VALUES ('FEE_MIN_BAL_PENALTY', 'Phi phat khong duy tri so du toi thieu', 'F', 'FLAT', 'MONTHLY', 'SYS_FEE_INCOME', SYSTIMESTAMP);

-- Mapping for CASA_VND
INSERT INTO DEPOSIT_PRODUCT_IFC_MAPPING (mapping_id, product_code, ifc_code, default_rate, min_rate, max_rate, is_mandatory, created_at)
VALUES (1, 'CASA_VND', 'INT_SAVINGS_DEMAND', 0.0020, 0.0010, 0.0050, 'Y', SYSTIMESTAMP);

INSERT INTO DEPOSIT_PRODUCT_IFC_MAPPING (mapping_id, product_code, ifc_code, default_rate, min_rate, max_rate, is_mandatory, created_at)
VALUES (2, 'CASA_VND', 'FEE_MONTHLY_MAINT', 10000.0000, 0.0000, 50000.0000, 'Y', SYSTIMESTAMP);

-- 6B. Credit IFCs
INSERT INTO CREDIT_IFC_DEFINITION (ifc_code, ifc_name, ifc_type, calculation_method, frequency, gl_income_mapping, gl_receivable_mapping, created_at)
VALUES ('NORMAL_INT', 'Lai suat cho vay trong han', 'I', 'PERCENTAGE', 'DAILY', 'SYS_INT_REVENUE', 'SYS_INT_RECEIVABLE', SYSTIMESTAMP);

INSERT INTO CREDIT_IFC_DEFINITION (ifc_code, ifc_name, ifc_type, calculation_method, frequency, gl_income_mapping, created_at)
VALUES ('OVERDUE_INT', 'Lai suat phat qua han 150%', 'I', 'PERCENTAGE', 'DAILY', 'SYS_INT_REVENUE', SYSTIMESTAMP);

INSERT INTO CREDIT_IFC_DEFINITION (ifc_code, ifc_name, ifc_type, calculation_method, frequency, gl_income_mapping, created_at)
VALUES ('ORIGINATION_FEE', 'Phi tham dinh ho so khoan vay', 'F', 'FLAT', 'ONETIME', 'SYS_FEE_INCOME', SYSTIMESTAMP);

INSERT INTO CREDIT_IFC_DEFINITION (ifc_code, ifc_name, ifc_type, calculation_method, frequency, gl_income_mapping, created_at)
VALUES ('PREPAY_FEE', 'Phi phat tat toan truoc han', 'F', 'PERCENTAGE', 'ONETIME', 'SYS_FEE_INCOME', SYSTIMESTAMP);

-- Mapping for LN_CONSUMER_VND
INSERT INTO CREDIT_PRODUCT_IFC_MAPPING (mapping_id, product_code, ifc_code, default_rate, min_rate, max_rate, is_mandatory, created_at)
VALUES (101, 'LN_CONSUMER_VND', 'NORMAL_INT', 0.0950, 0.0800, 0.1200, 'Y', SYSTIMESTAMP);

INSERT INTO CREDIT_PRODUCT_IFC_MAPPING (mapping_id, product_code, ifc_code, default_rate, min_rate, max_rate, is_mandatory, created_at)
VALUES (102, 'LN_CONSUMER_VND', 'OVERDUE_INT', 0.1425, 0.1425, 0.1425, 'Y', SYSTIMESTAMP);

INSERT INTO CREDIT_PRODUCT_IFC_MAPPING (mapping_id, product_code, ifc_code, default_rate, min_rate, max_rate, is_mandatory, created_at)
VALUES (103, 'LN_CONSUMER_VND', 'ORIGINATION_FEE', 500000.0000, 0.0000, 1000000.0000, 'N', SYSTIMESTAMP);

INSERT INTO CREDIT_PRODUCT_IFC_MAPPING (mapping_id, product_code, ifc_code, default_rate, min_rate, max_rate, is_mandatory, created_at)
VALUES (104, 'LN_CONSUMER_VND', 'PREPAY_FEE', 0.0200, 0.0100, 0.0300, 'N', SYSTIMESTAMP);

-- Mapping for LN_MORTGAGE_VND
INSERT INTO CREDIT_PRODUCT_IFC_MAPPING (mapping_id, product_code, ifc_code, default_rate, min_rate, max_rate, is_mandatory, created_at)
VALUES (105, 'LN_MORTGAGE_VND', 'NORMAL_INT', 0.0800, 0.0650, 0.1100, 'Y', SYSTIMESTAMP);

INSERT INTO CREDIT_PRODUCT_IFC_MAPPING (mapping_id, product_code, ifc_code, default_rate, min_rate, max_rate, is_mandatory, created_at)
VALUES (106, 'LN_MORTGAGE_VND', 'OVERDUE_INT', 0.1200, 0.1200, 0.1200, 'Y', SYSTIMESTAMP);

INSERT INTO CREDIT_PRODUCT_IFC_MAPPING (mapping_id, product_code, ifc_code, default_rate, min_rate, max_rate, is_mandatory, created_at)
VALUES (107, 'LN_MORTGAGE_VND', 'PREPAY_FEE', 0.0250, 0.0100, 0.0400, 'N', SYSTIMESTAMP);
