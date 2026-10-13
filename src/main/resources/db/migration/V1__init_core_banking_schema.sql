-- ============================================================================
-- V1__init_core_banking_schema.sql (ADVANCED ENTERPRISE CODELIST EDITION)
-- Incorporating:
-- 1. Dynamic CODELIST & CODELIST_VALUE architecture (No hardcoded status check constraints)
-- 2. Composite Idempotency Key (channel_id, idempotency_key)
-- 3. Pure Reversal / Storno lineage at transaction header level
-- 4. Three-tier Account Abstraction (GL_ACCOUNT, ACCOUNT_MAPPING, ACCOUNT_MAPPING_DETAIL)
-- 5. Mandatory explicit currencies on all financial postings
-- 6. Strict Hold balance & status invariants
-- ============================================================================

CREATE SEQUENCE CBS_GLOBAL_SEQ
    START WITH 10000000
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

-- ============================================================================
-- 0. DYNAMIC CODELIST ARCHITECTURE (Replacing hardcoded check constraints)
-- ============================================================================
CREATE TABLE CODELIST (
    code_group          VARCHAR2(30 CHAR)   NOT NULL, -- E.g. ACCT_STATUS, KYC_STATUS, TRX_STATUS
    code_group_name     VARCHAR2(100 CHAR)  NOT NULL,
    description         VARCHAR2(255 CHAR),
    is_system           CHAR(1)             DEFAULT 'Y' NOT NULL, -- 'Y' = cannot be deleted by users
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_codelist PRIMARY KEY (code_group)
);

CREATE TABLE CODELIST_VALUE (
    code_group          VARCHAR2(30 CHAR)   NOT NULL,
    code_value          VARCHAR2(30 CHAR)   NOT NULL, -- E.g. ACTIVE, DORMANT, BLOCKED, CLOSED
    display_name_vi     VARCHAR2(100 CHAR)  NOT NULL,
    display_name_en     VARCHAR2(100 CHAR)  NOT NULL,
    sort_order          NUMBER(3)           DEFAULT 1 NOT NULL,
    is_active           CHAR(1)             DEFAULT 'Y' NOT NULL,
    CONSTRAINT pk_codelist_val PRIMARY KEY (code_group, code_value),
    CONSTRAINT fk_cdval_group FOREIGN KEY (code_group) REFERENCES CODELIST (code_group)
);

-- ============================================================================
-- 1. OPERATIONAL CALENDAR & CONTROLS
-- ============================================================================
CREATE TABLE BANK_CALENDAR (
    calendar_date       DATE            NOT NULL,
    working_date        DATE            NOT NULL,
    is_working_day      CHAR(1)         DEFAULT 'Y' NOT NULL,
    fiscal_year         NUMBER(4)       NOT NULL,
    fiscal_period       NUMBER(2)       NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_bank_calendar PRIMARY KEY (calendar_date),
    CONSTRAINT chk_cal_working_day CHECK (is_working_day IN ('Y', 'N'))
);

CREATE TABLE SYSTEM_OPERATIONAL_STATUS (
    status_id           NUMBER(1)       DEFAULT 1 NOT NULL,
    current_working_date DATE           NOT NULL,
    is_system_cutoff    CHAR(1)         DEFAULT 'N' NOT NULL,
    last_eod_completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_sys_status PRIMARY KEY (status_id),
    CONSTRAINT chk_sys_singleton CHECK (status_id = 1),
    CONSTRAINT chk_sys_cutoff CHECK (is_system_cutoff IN ('Y', 'N'))
);

-- ============================================================================
-- 2. GENERAL LEDGER (GL) & ACCOUNT ABSTRACTION LAYER
-- ============================================================================
CREATE TABLE GL_ACCOUNT (
    gl_code             VARCHAR2(20 CHAR)   NOT NULL,
    gl_name             VARCHAR2(100 CHAR)  NOT NULL,
    account_type        VARCHAR2(15 CHAR)   NOT NULL, -- ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE (Validated via CODELIST)
    normal_balance      CHAR(1)             NOT NULL, -- 'D' or 'C'
    parent_gl_code      VARCHAR2(20 CHAR),
    gl_level            NUMBER(2)           DEFAULT 1 NOT NULL,
    is_postable         CHAR(1)             DEFAULT 'N' NOT NULL,
    currency            CHAR(3)             NOT NULL, -- Explicit currency mandatory
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_gl_account PRIMARY KEY (gl_code),
    CONSTRAINT fk_gl_parent FOREIGN KEY (parent_gl_code) REFERENCES GL_ACCOUNT (gl_code),
    CONSTRAINT chk_gl_norm_bal CHECK (normal_balance IN ('D', 'C')),
    CONSTRAINT chk_gl_postable CHECK (is_postable IN ('Y', 'N'))
);

CREATE INDEX idx_gl_parent ON GL_ACCOUNT (parent_gl_code);

CREATE TABLE ACCOUNT_MAPPING (
    mapping_id          VARCHAR2(30 CHAR)   NOT NULL, -- E.g. SYS_CASH_VAULT
    mapping_name        VARCHAR2(100 CHAR)  NOT NULL,
    account_type        VARCHAR2(15 CHAR)   NOT NULL,
    description         VARCHAR2(255 CHAR),
    CONSTRAINT pk_acct_mapping PRIMARY KEY (mapping_id)
);

CREATE TABLE ACCOUNT_MAPPING_DETAIL (
    detail_id           NUMBER(19)          NOT NULL,
    mapping_id          VARCHAR2(30 CHAR)   NOT NULL,
    branch_code         VARCHAR2(10 CHAR)   DEFAULT 'ALL' NOT NULL,
    currency            CHAR(3)             DEFAULT 'ALL' NOT NULL,
    product_code        VARCHAR2(10 CHAR)   DEFAULT 'ALL' NOT NULL,
    physical_gl_code    VARCHAR2(20 CHAR)   NOT NULL,
    is_active           CHAR(1)             DEFAULT 'Y' NOT NULL,
    CONSTRAINT pk_acct_map_detail PRIMARY KEY (detail_id),
    CONSTRAINT fk_map_parent FOREIGN KEY (mapping_id) REFERENCES ACCOUNT_MAPPING (mapping_id),
    CONSTRAINT fk_map_gl FOREIGN KEY (physical_gl_code) REFERENCES GL_ACCOUNT (gl_code),
    CONSTRAINT uq_acct_map_rule UNIQUE (mapping_id, branch_code, currency, product_code),
    CONSTRAINT chk_map_active CHECK (is_active IN ('Y', 'N'))
);

-- ============================================================================
-- 3. CUSTOMER (CIF) & DEPOSIT ACCOUNTS (CASA)
-- ============================================================================
CREATE TABLE CUSTOMER (
    customer_id         NUMBER(19)          NOT NULL,
    cif_number          VARCHAR2(20 CHAR)   NOT NULL,
    customer_type       VARCHAR2(30 CHAR)   NOT NULL, -- References CODELIST: CUST_TYPE
    full_name           VARCHAR2(150 CHAR)  NOT NULL,
    national_id         VARCHAR2(30 CHAR)   NOT NULL,
    tax_number          VARCHAR2(30 CHAR),
    kyc_status          VARCHAR2(30 CHAR)   DEFAULT 'PENDING' NOT NULL, -- References CODELIST: KYC_STATUS
    status              VARCHAR2(30 CHAR)   DEFAULT 'ACTIVE' NOT NULL,  -- References CODELIST: CUST_STATUS
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    created_working_date DATE               NOT NULL,
    CONSTRAINT pk_customer PRIMARY KEY (customer_id),
    CONSTRAINT uq_customer_cif UNIQUE (cif_number),
    CONSTRAINT uq_customer_national_id UNIQUE (national_id)
);

CREATE TABLE PRODUCT_CATALOG (
    product_code        VARCHAR2(10 CHAR)   NOT NULL,
    product_name        VARCHAR2(100 CHAR)  NOT NULL,
    currency            CHAR(3)             NOT NULL,
    default_gl_code     VARCHAR2(20 CHAR)   NOT NULL,
    interest_gl_code    VARCHAR2(20 CHAR)   NOT NULL,
    accrual_gl_code     VARCHAR2(20 CHAR)   NOT NULL,
    annual_rate         NUMBER(7, 4)        DEFAULT 0.0000 NOT NULL,
    day_count_basis     VARCHAR2(10 CHAR)   DEFAULT 'ACT_365' NOT NULL,
    min_balance         NUMBER(18, 4)       DEFAULT 0.0000 NOT NULL,
    is_active           CHAR(1)             DEFAULT 'Y' NOT NULL,
    CONSTRAINT pk_product_catalog PRIMARY KEY (product_code),
    CONSTRAINT fk_prod_gl FOREIGN KEY (default_gl_code) REFERENCES GL_ACCOUNT (gl_code),
    CONSTRAINT fk_prod_int_gl FOREIGN KEY (interest_gl_code) REFERENCES GL_ACCOUNT (gl_code),
    CONSTRAINT fk_prod_acc_gl FOREIGN KEY (accrual_gl_code) REFERENCES GL_ACCOUNT (gl_code),
    CONSTRAINT chk_prod_active CHECK (is_active IN ('Y', 'N'))
);

CREATE TABLE ACCOUNT (
    account_id          NUMBER(19)          NOT NULL,
    account_number      VARCHAR2(34 CHAR)   NOT NULL,
    customer_id         NUMBER(19)          NOT NULL,
    product_code        VARCHAR2(10 CHAR)   NOT NULL,
    gl_code             VARCHAR2(20 CHAR)   NOT NULL,
    branch_code         VARCHAR2(10 CHAR)   NOT NULL,
    currency            CHAR(3)             NOT NULL,
    ledger_balance      NUMBER(18, 4)       DEFAULT 0.0000 NOT NULL,
    hold_balance        NUMBER(18, 4)       DEFAULT 0.0000 NOT NULL,
    accrued_interest    NUMBER(18, 4)       DEFAULT 0.0000 NOT NULL,
    status              VARCHAR2(30 CHAR)   DEFAULT 'ACTIVE' NOT NULL, -- References CODELIST: ACCT_STATUS
    version             NUMBER(19)          DEFAULT 0 NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    created_working_date DATE               NOT NULL,
    CONSTRAINT pk_account PRIMARY KEY (account_id),
    CONSTRAINT uq_account_number UNIQUE (account_number),
    CONSTRAINT fk_acct_customer FOREIGN KEY (customer_id) REFERENCES CUSTOMER (customer_id),
    CONSTRAINT fk_acct_product FOREIGN KEY (product_code) REFERENCES PRODUCT_CATALOG (product_code),
    CONSTRAINT fk_acct_gl FOREIGN KEY (gl_code) REFERENCES GL_ACCOUNT (gl_code),
    CONSTRAINT chk_acct_hold_nonneg CHECK (hold_balance >= 0)
);

CREATE INDEX idx_acct_customer ON ACCOUNT (customer_id);

CREATE TABLE ACCOUNT_HOLD (
    hold_id             NUMBER(19)          NOT NULL,
    account_id          NUMBER(19)          NOT NULL,
    hold_amount         NUMBER(18, 4)       NOT NULL,
    reason_code         VARCHAR2(30 CHAR)   NOT NULL, -- References CODELIST: HOLD_REASON
    reference_id        VARCHAR2(64 CHAR),
    status              VARCHAR2(30 CHAR)   DEFAULT 'ACTIVE' NOT NULL, -- References CODELIST: HOLD_STATUS
    created_working_date DATE               NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    released_at         TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_account_hold PRIMARY KEY (hold_id),
    CONSTRAINT fk_hold_account FOREIGN KEY (account_id) REFERENCES ACCOUNT (account_id),
    CONSTRAINT chk_hold_amount CHECK (hold_amount > 0),
    CONSTRAINT chk_hold_status_dates CHECK (
        (status = 'ACTIVE' AND released_at IS NULL) OR
        (status IN ('RELEASED', 'SETTLED') AND released_at IS NOT NULL)
    )
);

CREATE INDEX idx_hold_account_active ON ACCOUNT_HOLD (account_id, status);

-- ============================================================================
-- 4. GOVERNED POSTING RULE MATRIX
-- ============================================================================
CREATE TABLE ACCOUNTING_RULE_DEFINITION (
    rule_id             NUMBER(19)          NOT NULL,
    trx_code            VARCHAR2(30 CHAR)   NOT NULL, -- References CODELIST: TRX_CODE
    entry_group         NUMBER(3)           DEFAULT 1 NOT NULL,
    entry_index         NUMBER(3)           NOT NULL,
    account_role        VARCHAR2(30 CHAR)   NOT NULL, -- References CODELIST: ACCOUNT_ROLE
    abstract_acct_name  VARCHAR2(50 CHAR),
    debit_or_credit     CHAR(1)             NOT NULL, -- 'D' or 'C'
    amount_source       VARCHAR2(30 CHAR)   NOT NULL, -- References CODELIST: AMOUNT_SOURCE
    requires_fee        CHAR(1)             DEFAULT 'N' NOT NULL,
    narration_template  VARCHAR2(255 CHAR)  NOT NULL,
    effective_from      DATE                NOT NULL,
    effective_to        DATE                DEFAULT TO_DATE('9999-12-31', 'YYYY-MM-DD') NOT NULL,
    is_active           CHAR(1)             DEFAULT 'Y' NOT NULL,
    CONSTRAINT pk_act_rule_def PRIMARY KEY (rule_id),
    CONSTRAINT chk_rule_drcr CHECK (debit_or_credit IN ('D', 'C')),
    CONSTRAINT chk_rule_req_fee CHECK (requires_fee IN ('Y', 'N')),
    CONSTRAINT chk_rule_active CHECK (is_active IN ('Y', 'N')),
    CONSTRAINT chk_rule_dates CHECK (effective_from <= effective_to)
);

CREATE INDEX idx_rule_trx_lookup ON ACCOUNTING_RULE_DEFINITION (trx_code, effective_from, effective_to, is_active);

-- ============================================================================
-- 5. HOT VS COLD DATA (Composite Idempotency & Reversal Lineage)
-- ============================================================================

-- A. HOT: In-Flight & Active Day Transactions
CREATE TABLE TRX_JOURNAL_ACTIVE (
    trx_id              NUMBER(19)          NOT NULL,
    trx_number          VARCHAR2(36 CHAR)   NOT NULL,
    channel_id          VARCHAR2(30 CHAR)   NOT NULL, -- References CODELIST: CHANNEL
    idempotency_key     VARCHAR2(64 CHAR)   NOT NULL,
    trx_code            VARCHAR2(30 CHAR)   NOT NULL, -- References CODELIST: TRX_CODE
    working_date        DATE                NOT NULL,
    status              VARCHAR2(30 CHAR)   NOT NULL, -- References CODELIST: TRX_STATUS
    maker_id            VARCHAR2(30 CHAR)   NOT NULL,
    checker_id          VARCHAR2(30 CHAR),
    
    -- Pure Reversal Header Lineage
    is_reversed         CHAR(1)             DEFAULT 'N' NOT NULL,
    original_trx_id     NUMBER(19),                   -- Links to original trx if this is a reversal
    reversal_reason     VARCHAR2(255 CHAR),
    
    payload_data        CLOB,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_trx_active PRIMARY KEY (trx_id),
    CONSTRAINT uq_trx_active_num UNIQUE (trx_number),
    -- Composite Idempotency Key (channel_id, idempotency_key)
    CONSTRAINT uq_trx_act_composite_idemp UNIQUE (channel_id, idempotency_key),
    CONSTRAINT fk_trx_act_orig FOREIGN KEY (original_trx_id) REFERENCES TRX_JOURNAL_ACTIVE (trx_id),
    CONSTRAINT chk_trx_act_reversed CHECK (is_reversed IN ('Y', 'N'))
);

CREATE TABLE JOURNAL_ENTRY_ACTIVE (
    entry_id            NUMBER(19)          NOT NULL,
    trx_id              NUMBER(19)          NOT NULL,
    entry_group         NUMBER(3)           DEFAULT 1 NOT NULL,
    entry_index         NUMBER(4)           NOT NULL,
    account_id          NUMBER(19),
    gl_code             VARCHAR2(20 CHAR)   NOT NULL,
    dr_cr               CHAR(1)             NOT NULL,
    amount              NUMBER(18, 4)       NOT NULL,
    currency            CHAR(3)             NOT NULL,
    value_date          DATE                NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_entry_active PRIMARY KEY (entry_id),
    CONSTRAINT fk_entry_act_trx FOREIGN KEY (trx_id) REFERENCES TRX_JOURNAL_ACTIVE (trx_id),
    CONSTRAINT fk_entry_act_acct FOREIGN KEY (account_id) REFERENCES ACCOUNT (account_id),
    CONSTRAINT fk_entry_act_gl FOREIGN KEY (gl_code) REFERENCES GL_ACCOUNT (gl_code),
    CONSTRAINT chk_entry_act_drcr CHECK (dr_cr IN ('D', 'C')),
    CONSTRAINT chk_entry_act_amt CHECK (amount > 0),
    CONSTRAINT uq_entry_act_leg UNIQUE (trx_id, entry_group, entry_index)
);

CREATE INDEX idx_entry_act_trx ON JOURNAL_ENTRY_ACTIVE (trx_id);
CREATE INDEX idx_entry_act_acct ON JOURNAL_ENTRY_ACTIVE (account_id);

-- B. COLD: Permanent History Archive
CREATE TABLE TRX_JOURNAL_DONE (
    trx_id              NUMBER(19)          NOT NULL,
    trx_number          VARCHAR2(36 CHAR)   NOT NULL,
    channel_id          VARCHAR2(30 CHAR)   NOT NULL,
    idempotency_key     VARCHAR2(64 CHAR)   NOT NULL,
    trx_code            VARCHAR2(30 CHAR)   NOT NULL,
    working_date        DATE                NOT NULL,
    status              VARCHAR2(30 CHAR)   NOT NULL,
    maker_id            VARCHAR2(30 CHAR)   NOT NULL,
    checker_id          VARCHAR2(30 CHAR),
    is_reversed         CHAR(1)             NOT NULL,
    original_trx_id     NUMBER(19),
    reversal_reason     VARCHAR2(255 CHAR),
    payload_data        CLOB,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    archived_at         TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_trx_done PRIMARY KEY (trx_id),
    CONSTRAINT uq_trx_done_num UNIQUE (trx_number),
    -- Cold Composite Idempotency Key
    CONSTRAINT uq_trx_done_composite_idemp UNIQUE (channel_id, idempotency_key, working_date)
);

CREATE INDEX idx_trx_done_idemp ON TRX_JOURNAL_DONE (channel_id, idempotency_key);
CREATE INDEX idx_trx_done_date ON TRX_JOURNAL_DONE (working_date);

CREATE TABLE JOURNAL_ENTRY_DONE (
    entry_id            NUMBER(19)          NOT NULL,
    trx_id              NUMBER(19)          NOT NULL,
    entry_group         NUMBER(3)           NOT NULL,
    entry_index         NUMBER(4)           NOT NULL,
    account_id          NUMBER(19),
    gl_code             VARCHAR2(20 CHAR)   NOT NULL,
    dr_cr               CHAR(1)             NOT NULL,
    amount              NUMBER(18, 4)       NOT NULL,
    currency            CHAR(3)             NOT NULL,
    value_date          DATE                NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    archived_at         TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_entry_done PRIMARY KEY (entry_id),
    CONSTRAINT fk_entry_done_trx FOREIGN KEY (trx_id) REFERENCES TRX_JOURNAL_DONE (trx_id),
    CONSTRAINT uq_entry_done_leg UNIQUE (trx_id, entry_group, entry_index)
);

CREATE INDEX idx_entry_done_trx ON JOURNAL_ENTRY_DONE (trx_id);
CREATE INDEX idx_entry_done_acct_date ON JOURNAL_ENTRY_DONE (account_id, value_date);
CREATE INDEX idx_entry_done_gl_date ON JOURNAL_ENTRY_DONE (gl_code, value_date);

-- ============================================================================
-- 6. EOD BALANCE BUCKETS
-- ============================================================================
CREATE TABLE ACCOUNT_BALANCE_BUCKET (
    account_id          NUMBER(19)          NOT NULL,
    working_date        DATE                NOT NULL,
    closing_balance     NUMBER(18, 4)       NOT NULL,
    daily_debit_sum     NUMBER(18, 4)       DEFAULT 0.0000 NOT NULL,
    daily_credit_sum    NUMBER(18, 4)       DEFAULT 0.0000 NOT NULL,
    accrued_interest_day NUMBER(18, 4)      DEFAULT 0.0000 NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_acct_bal_bucket PRIMARY KEY (account_id, working_date),
    CONSTRAINT fk_bucket_account FOREIGN KEY (account_id) REFERENCES ACCOUNT (account_id)
);
