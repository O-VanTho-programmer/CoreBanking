-- ============================================================================
-- V21: Deposit Product Hierarchy (CASA / FD), Rollover Options & VIP Interest Margin
-- 1. Alter DPT_PRODUCT_CATALOG with product_type, min_open_amount, min_accrual_balance, max_interest_margin
-- 2. Alter ACCOUNT with product_type, base_interest_rate, interest_margin, effective_rate, term_months,
--    maturity_date, rollover_option, margin_reason, linked_casa_account_id
-- 3. Upgrade FORM_ACCOUNT_OPEN with FD fields, rollover options, and VIP margin workflow
-- 4. Upgrade FORM_DPT_CATALOGUE columns with product_type, min_open_amount, min_accrual_balance
-- ============================================================================

-- 1. Alter DPT_PRODUCT_CATALOG
ALTER TABLE DPT_PRODUCT_CATALOG ADD (
    product_type VARCHAR2(10 CHAR) DEFAULT 'CASA' NOT NULL,
    min_open_amount NUMBER(18, 4) DEFAULT 0 NOT NULL,
    min_accrual_balance NUMBER(18, 4) DEFAULT 0 NOT NULL,
    max_interest_margin NUMBER(8, 4) DEFAULT 0.0200 NOT NULL
);

ALTER TABLE DPT_PRODUCT_CATALOG ADD CONSTRAINT chk_dpt_product_type CHECK (product_type IN ('CASA', 'FD'));

-- Update product catalog items
UPDATE DPT_PRODUCT_CATALOG
SET product_type = 'CASA',
    min_open_amount = 0.0000,
    min_accrual_balance = 50000.0000,
    max_interest_margin = 0.0050
WHERE product_code = 'CASA_VND';

UPDATE DPT_PRODUCT_CATALOG
SET product_type = 'CASA',
    min_open_amount = 1000000.0000,
    min_accrual_balance = 1000000.0000,
    max_interest_margin = 0.0100
WHERE product_code = 'CASA_CORP';

UPDATE DPT_PRODUCT_CATALOG
SET product_type = 'FD',
    min_open_amount = 5000000.0000,
    min_accrual_balance = 1000000.0000,
    max_interest_margin = 0.0150
WHERE product_code = 'SAV_FLEXI';

UPDATE DPT_PRODUCT_CATALOG
SET product_type = 'FD',
    min_open_amount = 10000000.0000,
    min_accrual_balance = 10000000.0000,
    max_interest_margin = 0.0200
WHERE product_code = 'SAV_TERM';

-- 2. Alter ACCOUNT
ALTER TABLE ACCOUNT ADD (
    product_type VARCHAR2(10 CHAR) DEFAULT 'CASA' NOT NULL,
    base_interest_rate NUMBER(8, 4) DEFAULT 0.0050 NOT NULL,
    interest_margin NUMBER(8, 4) DEFAULT 0.0000 NOT NULL,
    effective_rate NUMBER(8, 4) DEFAULT 0.0050 NOT NULL,
    term_months NUMBER(5),
    maturity_date DATE,
    rollover_option VARCHAR2(30 CHAR),
    margin_reason VARCHAR2(50 CHAR),
    linked_casa_account_id NUMBER(19)
);

-- Backfill existing account rates from their product catalog
UPDATE ACCOUNT a
SET a.base_interest_rate = (
        SELECT NVL(p.annual_rate, 0.0050)
        FROM DPT_PRODUCT_CATALOG p
        WHERE p.product_code = a.product_code
    ),
    a.effective_rate = (
        SELECT NVL(p.annual_rate, 0.0050)
        FROM DPT_PRODUCT_CATALOG p
        WHERE p.product_code = a.product_code
    ),
    a.product_type = (
        SELECT NVL(p.product_type, 'CASA')
        FROM DPT_PRODUCT_CATALOG p
        WHERE p.product_code = a.product_code
    );

-- 3. Upgrade FORM_DPT_CATALOGUE
UPDATE FORM
SET schema_payload = TO_CLOB('{
  "formCode": "FORM_DPT_CATALOGUE",
  "trxCode": "BO_DPT_CAT",
  "module": "DPT",
  "formType": "SEARCH_GRID",
  "title": "Deposit Product Catalogue Directory",
  "description": "Inspect product packages, interest rates, GL mappings, and minimum balance requirements.",
  "searchEndpoint": "/admin/catalogue/deposit",
  "quickSearch": {
    "placeholder": "Search by Product Code, Product Name, Currency...",
    "paramName": "keyword"
  },
  "columns": [
    { "key": "productCode", "label": "Product Code", "type": "MONO_BOLD" },
    { "key": "productName", "label": "Product Name", "type": "TEXT" },
    { "key": "productType", "label": "Type", "type": "BADGE" },
    { "key": "currency", "label": "Currency", "type": "BADGE" },
    { "key": "annualRate", "label": "Base Rate", "type": "TEXT" },
    { "key": "minOpenAmount", "label": "Min Open Amount", "type": "CURRENCY", "currencyKey": "currency" },
    { "key": "minAccrualBalance", "label": "Min Accrual Balance", "type": "CURRENCY", "currencyKey": "currency" },
    { "key": "defaultGlCode", "label": "GL Class", "type": "TEXT" },
    { "key": "isActive", "label": "Status", "type": "STATUS_BADGE" }
  ]
}')
WHERE form_code = 'FORM_DPT_CATALOGUE';

-- 4. Upgrade FORM_ACCOUNT_OPEN (Split with TO_CLOB concatenation to avoid ORA-01704)
UPDATE FORM
SET schema_payload = TO_CLOB('{
  "formCode": "FORM_ACCOUNT_OPEN",
  "trxCode": "OPEN_CASA",
  "title": "Open Deposit Account",
  "submitEndpoint": "/accounts/open",
  "fields": [
    {
      "name": "cifNumber",
      "label": "Customer CIF Number",
      "type": "TEXT",
      "required": true,
      "placeholder": "Enter CIF number...",
      "steps": ["LOOKUP_CUSTOMER"],
      "look_up": {
        "is_enable": true,
        "modal_form": "FORM_CUST_PROFILE",
        "value_key": "cifNumber",
        "mapping": {
          "cifNumber": "cifNumber",
          "customerName": "fullName"
        }
      }
    },
    {
      "name": "customerName",
      "label": "Customer Name",
      "type": "TEXT",
      "readOnly": true
    },
    {
      "name": "productCode",
      "label": "Deposit Product Code",
      "type": "TEXT",
      "defaultValue": "CASA_VND",
      "required": true,
      "placeholder": "Select or enter product code...",
      "steps": ["EVALUATE_DEPOSIT_PRODUCT"],
      "look_up": {
        "is_enable": true,
        "modal_form": "FORM_DPT_CATALOGUE",
        "value_key": "productCode",
        "mapping": {
          "productCode": "productCode",
          "currency": "currency",
          "productType": "productType",
          "baseInterestRate": "annualRate"
        }
      }
    },
    {
      "name": "productType",
      "label": "Product Category",
      "type": "TEXT",
      "defaultValue": "CASA",
      "readOnly": true
    },
    {
      "name": "branchCode",
      "label": "Branch Code",
      "type": "TEXT",
      "defaultValue": "001",
      "required": true
    },
    {
      "name": "currency",
      "label": "Currency",
      "type": "TEXT",
      "defaultValue": "VND",
      "readOnly": true
    },
    {
      "name": "initialDeposit",
      "label": "Initial Placement Amount (VND)",
      "type": "CURRENCY",
      "defaultValue": 0,
      "placeholder": "0.0000",
      "steps": ["EVALUATE_DEPOSIT_PRODUCT"]
    },') || TO_CLOB('
    {
      "name": "termMonths",
      "label": "Deposit Term (Months)",
      "type": "SELECT",
      "defaultValue": 12,
      "options": [
        { "value": "1", "label": "1 Month" },
        { "value": "3", "label": "3 Months" },
        { "value": "6", "label": "6 Months" },
        { "value": "12", "label": "12 Months" },
        { "value": "24", "label": "24 Months" },
        { "value": "36", "label": "36 Months" }
      ],
      "steps": ["EVALUATE_DEPOSIT_PRODUCT"]
    },
    {
      "name": "rolloverOption",
      "label": "Maturity Rollover Instruction",
      "type": "SELECT",
      "defaultValue": "PRINCIPAL_AND_INTEREST",
      "options": [
        { "value": "PRINCIPAL_AND_INTEREST", "label": "Auto-rollover Principal + Interest" },
        { "value": "PRINCIPAL_ONLY", "label": "Rollover Principal Only (Interest to CASA)" },
        { "value": "NO_ROLLOVER", "label": "Do Not Rollover (Close to CASA at Maturity)" }
      ],
      "steps": ["EVALUATE_DEPOSIT_PRODUCT"]
    },
    {
      "name": "linkedCasaAccountNumber",
      "label": "Linked Settlement CASA Account",
      "type": "TEXT",
      "placeholder": "Enter 16-digit CASA account for settlement...",
      "steps": ["LOOKUP_LINKED_CASA"]
    },
    {
      "name": "baseInterestRate",
      "label": "Base Annual Interest Rate (%/yr)",
      "type": "NUMBER",
      "defaultValue": 0.005,
      "readOnly": true
    },
    {
      "name": "requestInterestMargin",
      "label": "Request VIP Margin",
      "type": "CHECKBOX",
      "defaultValue": false,
      "steps": ["EVALUATE_DEPOSIT_PRODUCT"]
    },
    {
      "name": "marginReason",
      "label": "Margin Justification Reason",
      "type": "SELECT",
      "placeholder": "Select VIP/Margin approval reason...",
      "options": [
        { "value": "VIP_CUSTOMER", "label": "Priority / VIP Customer Segment" },
        { "value": "LARGE_DEPOSIT", "label": "High Balance Deposit (>= 500M VND)" },
        { "value": "BRANCH_DIRECTOR_APPROVAL", "label": "Branch Director Special Approval" },
        { "value": "PROMOTIONAL_CAMPAIGN", "label": "Loyalty / Promotional Campaign" }
      ],
      "steps": ["EVALUATE_DEPOSIT_PRODUCT"]
    },
    {
      "name": "interestMargin",
      "label": "VIP Margin Spread (%/yr)",
      "type": "NUMBER",
      "defaultValue": 0,
      "placeholder": "e.g. 0.0050 for +0.50%",
      "steps": ["EVALUATE_DEPOSIT_PRODUCT"]
    },
    {
      "name": "effectiveRate",
      "label": "Effective Interest Rate (%/yr)",
      "type": "NUMBER",
      "defaultValue": 0.005,
      "readOnly": true
    }
  ],
  "actions": [
    {
      "code": "BTN_OPEN_CASA",
      "label": "Submit",
      "style": "primary",
      "actionType": "SUBMIT_TRX",
      "endpoint": "/accounts/open"
    }
  ]
}')
WHERE form_code = 'FORM_ACCOUNT_OPEN';
