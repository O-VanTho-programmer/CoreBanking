-- ============================================================================
-- V22: FD Term-based Interest Rate Matrix, Codelists, and Rollover Architecture
-- 1. Create FD_INTEREST_RATE_MATRIX table
-- 2. Add rollover_rate_rule to DPT_PRODUCT_CATALOG
-- 3. Seed CODELIST & CODELIST_VALUE for ROLLOVER_OPTION, ROLLOVER_RATE_RULE, MARGIN_REASON
-- 4. Seed initial term-based rates for SAV_TERM and SAV_FLEXI
-- 5. Update FORM_ACCOUNT_OPEN schema payload
-- ============================================================================

-- 1. Create FD_INTEREST_RATE_MATRIX
CREATE TABLE FD_INTEREST_RATE_MATRIX (
    id                  NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_code        VARCHAR2(20 CHAR) NOT NULL,
    term_months         NUMBER(4) NOT NULL,
    base_rate           NUMBER(8, 4) NOT NULL,
    effective_from      DATE NOT NULL,
    effective_to        DATE,
    status              VARCHAR2(20 CHAR) DEFAULT 'ACTIVE' NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_fd_rate_product FOREIGN KEY (product_code) REFERENCES DPT_PRODUCT_CATALOG(product_code)
);

CREATE INDEX idx_fd_rate_lookup 
ON FD_INTEREST_RATE_MATRIX (product_code, term_months, effective_from, effective_to, status);

-- 2. Add rollover_rate_rule to DPT_PRODUCT_CATALOG
ALTER TABLE DPT_PRODUCT_CATALOG ADD (
    rollover_rate_rule VARCHAR2(30 CHAR) DEFAULT 'CURRENT_MARKET_RATE' NOT NULL
);

-- 3. Seed CODELIST & CODELIST_VALUE
-- Group 1: ROLLOVER_OPTION
INSERT INTO CODELIST (code_group, code_group_name, description, is_system)
VALUES ('ROLLOVER_OPTION', 'FD Rollover Instructions', 'Rollover options for Fixed Deposit on maturity', 'Y');

INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order, is_active)
VALUES ('ROLLOVER_OPTION', 'PRINCIPAL_AND_INTEREST', 'Tự động tái tục Cả gốc và lãi', 'Auto-rollover Principal + Interest', 1, 'Y');
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order, is_active)
VALUES ('ROLLOVER_OPTION', 'PRINCIPAL_ONLY', 'Tái tục gốc, chi trả lãi vào CASA', 'Rollover Principal Only (Interest to CASA)', 2, 'Y');
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order, is_active)
VALUES ('ROLLOVER_OPTION', 'NO_ROLLOVER', 'Không tái tục (Tất toán chuyển vào CASA)', 'Do Not Rollover (Close to CASA at Maturity)', 3, 'Y');

-- Group 2: ROLLOVER_RATE_RULE
INSERT INTO CODELIST (code_group, code_group_name, description, is_system)
VALUES ('ROLLOVER_RATE_RULE', 'FD Rollover Rate Rules', 'Pricing rule applied to FD on automatic rollover', 'Y');

INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order, is_active)
VALUES ('ROLLOVER_RATE_RULE', 'CURRENT_MARKET_RATE', 'Lãi suất thị trường hiện hành tại ngày đáo hạn', 'Current Market Rate at Maturity', 1, 'Y');
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order, is_active)
VALUES ('ROLLOVER_RATE_RULE', 'ORIGINAL_CONTRACT_RATE', 'Giữ nguyên lãi suất hợp đồng cũ', 'Original Contract Rate', 2, 'Y');
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order, is_active)
VALUES ('ROLLOVER_RATE_RULE', 'MANUAL_CONFIRMATION', 'Chờ khách hàng/giao dịch viên xác nhận tại quầy', 'Manual Confirmation Required', 3, 'Y');

-- Group 3: MARGIN_REASON
INSERT INTO CODELIST (code_group, code_group_name, description, is_system)
VALUES ('MARGIN_REASON', 'Interest Margin Justification Reasons', 'Reasons for approving VIP interest margin', 'Y');

INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order, is_active)
VALUES ('MARGIN_REASON', 'VIP_CUSTOMER', 'Khách hàng VIP / Khách hàng ưu tiên', 'VIP / Priority Customer Segment', 1, 'Y');
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order, is_active)
VALUES ('MARGIN_REASON', 'HIGH_BALANCE', 'Số dư tiền gửi lớn (>= 500M VND)', 'High Balance Deposit (>= 500M VND)', 2, 'Y');
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order, is_active)
VALUES ('MARGIN_REASON', 'CUSTOMER_RETENTION', 'Chăm sóc và giữ chân khách hàng', 'Customer Retention Campaign', 3, 'Y');
INSERT INTO CODELIST_VALUE (code_group, code_value, display_name_vi, display_name_en, sort_order, is_active)
VALUES ('MARGIN_REASON', 'SPECIAL_APPROVAL', 'Ngoại lệ được Ban Lãnh đạo phê duyệt', 'Special Management Approval', 4, 'Y');

-- 4. Seed initial term-based rates for SAV_TERM and SAV_FLEXI
INSERT INTO FD_INTEREST_RATE_MATRIX (product_code, term_months, base_rate, effective_from, status)
VALUES ('SAV_TERM', 1, 0.0350, DATE '2026-01-01', 'ACTIVE');
INSERT INTO FD_INTEREST_RATE_MATRIX (product_code, term_months, base_rate, effective_from, status)
VALUES ('SAV_TERM', 3, 0.0400, DATE '2026-01-01', 'ACTIVE');
INSERT INTO FD_INTEREST_RATE_MATRIX (product_code, term_months, base_rate, effective_from, status)
VALUES ('SAV_TERM', 6, 0.0500, DATE '2026-01-01', 'ACTIVE');
INSERT INTO FD_INTEREST_RATE_MATRIX (product_code, term_months, base_rate, effective_from, status)
VALUES ('SAV_TERM', 12, 0.0550, DATE '2026-01-01', 'ACTIVE');
INSERT INTO FD_INTEREST_RATE_MATRIX (product_code, term_months, base_rate, effective_from, status)
VALUES ('SAV_TERM', 24, 0.0600, DATE '2026-01-01', 'ACTIVE');
INSERT INTO FD_INTEREST_RATE_MATRIX (product_code, term_months, base_rate, effective_from, status)
VALUES ('SAV_TERM', 36, 0.0620, DATE '2026-01-01', 'ACTIVE');

INSERT INTO FD_INTEREST_RATE_MATRIX (product_code, term_months, base_rate, effective_from, status)
VALUES ('SAV_FLEXI', 1, 0.0320, DATE '2026-01-01', 'ACTIVE');
INSERT INTO FD_INTEREST_RATE_MATRIX (product_code, term_months, base_rate, effective_from, status)
VALUES ('SAV_FLEXI', 3, 0.0380, DATE '2026-01-01', 'ACTIVE');
INSERT INTO FD_INTEREST_RATE_MATRIX (product_code, term_months, base_rate, effective_from, status)
VALUES ('SAV_FLEXI', 6, 0.0450, DATE '2026-01-01', 'ACTIVE');
INSERT INTO FD_INTEREST_RATE_MATRIX (product_code, term_months, base_rate, effective_from, status)
VALUES ('SAV_FLEXI', 12, 0.0480, DATE '2026-01-01', 'ACTIVE');

-- 5. Update FORM_ACCOUNT_OPEN schema payload
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
      "label": "Deposit Term",
      "type": "SELECT",
      "placeholder": "Select deposit term...",
      "options": [],
      "steps": ["EVALUATE_DEPOSIT_PRODUCT"]
    },
    {
      "name": "rolloverOption",
      "label": "Maturity Rollover Instruction",
      "type": "SELECT",
      "defaultValue": "PRINCIPAL_AND_INTEREST",
      "optionsEndpoint": "/codelists/ROLLOVER_OPTION",
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
      "optionsEndpoint": "/codelists/MARGIN_REASON",
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
