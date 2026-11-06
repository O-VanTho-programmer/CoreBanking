-- ============================================================================
-- V18: Seed Deposit Products, Upgrade FORM_DPT_CATALOGUE & Enable Look_Up in FORM_ACCOUNT_OPEN
-- ============================================================================

-- 1. Seed additional deposit products into DPT_PRODUCT_CATALOG
MERGE INTO DPT_PRODUCT_CATALOG dest
USING (SELECT 'CASA_CORP' AS product_code FROM DUAL) src
ON (dest.product_code = src.product_code)
WHEN NOT MATCHED THEN
    INSERT (product_code, product_name, currency, default_gl_code, interest_gl_code, accrual_gl_code, annual_rate, day_count_basis, min_balance, is_active)
    VALUES ('CASA_CORP', 'Corporate Demand Deposit VND', 'VND', '4211', '8011', '4911', 0.0020, 'ACT_365', 1000000.0000, 'Y');

MERGE INTO DPT_PRODUCT_CATALOG dest
USING (SELECT 'SAV_FLEXI' AS product_code FROM DUAL) src
ON (dest.product_code = src.product_code)
WHEN NOT MATCHED THEN
    INSERT (product_code, product_name, currency, default_gl_code, interest_gl_code, accrual_gl_code, annual_rate, day_count_basis, min_balance, is_active)
    VALUES ('SAV_FLEXI', 'Flexible Term Savings Deposit', 'VND', '4211', '8011', '4911', 0.0350, 'ACT_365', 5000000.0000, 'Y');

MERGE INTO DPT_PRODUCT_CATALOG dest
USING (SELECT 'SAV_TERM' AS product_code FROM DUAL) src
ON (dest.product_code = src.product_code)
WHEN NOT MATCHED THEN
    INSERT (product_code, product_name, currency, default_gl_code, interest_gl_code, accrual_gl_code, annual_rate, day_count_basis, min_balance, is_active)
    VALUES ('SAV_TERM', '12-Month High Yield Fixed Deposit', 'VND', '4211', '8011', '4911', 0.0550, 'ACT_365', 10000000.0000, 'Y');

-- 2. Upgrade FORM_DPT_CATALOGUE to SEARCH_GRID
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
    { "key": "currency", "label": "Currency", "type": "BADGE" },
    { "key": "annualRate", "label": "Annual Rate", "type": "TEXT" },
    { "key": "defaultGlCode", "label": "GL Class", "type": "TEXT" },
    { "key": "minBalance", "label": "Min Balance", "type": "CURRENCY", "currencyKey": "currency" },
    { "key": "isActive", "label": "Status", "type": "STATUS_BADGE" }
  ]
}')
WHERE form_code = 'FORM_DPT_CATALOGUE';

-- 3. Update FORM_ACCOUNT_OPEN with look_up enabled on productCode
UPDATE FORM
SET schema_payload = TO_CLOB('{
  "formCode": "FORM_ACCOUNT_OPEN",
  "trxCode": "OPEN_CASA",
  "title": "Open Deposit Account (CASA)",
  "submitEndpoint": "/accounts/open",
  "fields": [
    {
      "name": "cifNumber",
      "label": "Customer CIF Number",
      "type": "TEXT",
      "required": true,
      "placeholder": "Enter CIF number...",
      "steps": ["LOOKUP_CUSTOMER"]
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
      "look_up": {
        "is_enable": true,
        "modal_form": "FORM_DPT_CATALOGUE",
        "value_key": "productCode",
        "mapping": {
          "productCode": "productCode",
          "currency": "currency"
        }
      }
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
    }
  ],
  "actions": [
    {
      "code": "BTN_OPEN_CASA",
      "label": "Open Deposit Account",
      "style": "primary",
      "actionType": "SUBMIT_TRX",
      "endpoint": "/accounts/open"
    }
  ]
}')
WHERE form_code = 'FORM_ACCOUNT_OPEN';
