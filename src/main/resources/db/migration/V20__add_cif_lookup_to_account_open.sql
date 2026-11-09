-- ============================================================================
-- V20: Add Customer CIF Lookup to FORM_ACCOUNT_OPEN
-- Enables inline lookup 🔍 on cifNumber pointing to FORM_CUST_PROFILE
-- Maps selected customer record (cifNumber, fullName -> customerName)
-- ============================================================================

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
      "label": "Submit",
      "style": "primary",
      "actionType": "SUBMIT_TRX",
      "endpoint": "/accounts/open"
    }
  ]
}')
WHERE form_code = 'FORM_ACCOUNT_OPEN';
