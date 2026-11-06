-- ============================================================================
-- V17: Normalize Endpoints (strip /api/v1) and Remove Icons & '+' from Action Labels
-- ============================================================================

-- 1. Global normalization: Replace any "/api/v1/ with "/ in all schema_payloads
UPDATE FORM
SET schema_payload = REPLACE(schema_payload, '"/api/v1/', '"/');

-- 2. Global normalization: Strip '+ ' from all action labels
UPDATE FORM
SET schema_payload = REPLACE(schema_payload, '"label": "+ ', '"label": "');

UPDATE FORM
SET schema_payload = REPLACE(schema_payload, '"label": "+', '"label": "');

-- 3. Explicitly update FORM_CUST_PROFILE with clean actions and endpoints
UPDATE FORM
SET schema_payload = TO_CLOB('{
  "formCode": "FORM_CUST_PROFILE",
  "trxCode": "CUST_PROFILE",
  "module": "CUST",
  "formType": "SEARCH_GRID",
  "title": "Customer Profile & CIF Directory",
  "description": "Search, filter, and inspect customer profiles across all banking segments.",
  "searchEndpoint": "/customers/search",
  "topActions": [
    {
      "code": "BTN_ADD_CUSTOMER",
      "label": "Add New Customer",
      "style": "primary",
      "actionType": "NAVIGATE_FORM",
      "targetFormCode": "FORM_CUST_CREATE"
    }
  ],
  "quickSearch": {
    "placeholder": "Search by CIF Number, Full Name, National ID, Phone...",
    "paramName": "keyword"
  },
  "advanceFilters": [
    { "name": "customerType", "label": "Customer Type", "type": "SELECT", "optionsEndpoint": "/customers/catalogue", "optionLabel": "typeName", "optionValue": "typeCode", "placeholder": "All Customer Types" },
    { "name": "fullName", "label": "Full Legal Name", "type": "TEXT", "placeholder": "e.g. Nguyen Van A..." },
    { "name": "phoneNumber", "label": "Phone Number", "type": "TEXT", "placeholder": "e.g. 0901234567..." },
    { "name": "emailAddress", "label": "Email Address", "type": "TEXT", "placeholder": "e.g. alex@corp.com..." },
    { "name": "gender", "label": "Gender", "type": "SELECT", "options": [{ "label": "Male", "value": "MALE" }, { "label": "Female", "value": "FEMALE" }, { "label": "Other / Corporate", "value": "OTHER" }] },
    { "name": "status", "label": "Status", "type": "SELECT", "options": [{ "label": "Active", "value": "ACTIVE" }, { "label": "Suspended", "value": "SUSPENDED" }, { "label": "Deceased", "value": "DECEASED" }] },
    { "name": "kycStatus", "label": "KYC Status", "type": "SELECT", "options": [{ "label": "Verified", "value": "VERIFIED" }, { "label": "Pending", "value": "PENDING" }, { "label": "Rejected", "value": "REJECTED" }] },
    { "name": "province", "label": "Province / City", "type": "TEXT", "placeholder": "e.g. Ho Chi Minh..." }
  ],') || TO_CLOB('
  "columns": [
    { "key": "cifNumber", "label": "CIF Number", "type": "MONO_BOLD" },
    { "key": "fullName", "label": "Customer Name", "type": "NAME_WITH_SUB", "subKey": "gender" },
    { "key": "customerType", "label": "Type & Segment", "type": "BADGE" },
    { "key": "nationalId", "label": "National ID / Tax", "type": "ID_WITH_TAX", "subKey": "taxNumber" },
    { "key": "phoneNumber", "label": "Contact", "type": "CONTACT", "subKey": "emailAddress" },
    { "key": "streetAddress", "label": "Address", "type": "ADDRESS", "addressKeys": ["streetAddress", "district", "province"] },
    { "key": "status", "label": "Status", "type": "STATUS_DOUBLE", "subKey": "kycStatus" }
  ],
  "rowActions": [
    {
      "code": "ACT_OPEN_CASA",
      "label": "CASA",
      "style": "emerald",
      "title": "Open CASA Account for this customer",
      "targetFormCode": "FORM_ACCOUNT_OPEN",
      "paramsMapping": { "customerId": "customerId", "cifNumber": "cifNumber", "customerName": "fullName" }
    },
    {
      "code": "ACT_OPEN_LOAN",
      "label": "Loan",
      "style": "indigo",
      "title": "Open Credit Loan Account for this customer",
      "targetFormCode": "FORM_CREDIT_ACCOUNT_OPEN",
      "paramsMapping": { "customerId": "customerId", "cifNumber": "cifNumber", "customerName": "fullName" }
    }
  ]
}')
WHERE form_code = 'FORM_CUST_PROFILE';

-- 4. Explicitly update FORM_DPT_INFO with clean actions and endpoints
UPDATE FORM
SET schema_payload = TO_CLOB('{
  "formCode": "FORM_DPT_INFO",
  "trxCode": "INQ_DPT",
  "module": "DPT",
  "formType": "SEARCH_GRID",
  "title": "Deposit Account Directory & Inquiry",
  "description": "Search, filter, and inspect CASA deposit accounts, real-time balances, and account status.",
  "searchEndpoint": "/accounts/search",
  "topActions": [
    {
      "code": "BTN_OPEN_ACCOUNT",
      "label": "Open Deposit Account",
      "style": "primary",
      "actionType": "NAVIGATE_FORM",
      "targetFormCode": "FORM_ACCOUNT_OPEN"
    }
  ],
  "quickSearch": {
    "placeholder": "Search by Account Number, Customer Name...",
    "paramName": "keyword"
  },
  "advanceFilters": [
    { "name": "accountNumber", "label": "Account Number", "type": "TEXT", "placeholder": "e.g. 001421..." },
    { "name": "customerName", "label": "Account Holder Name", "type": "TEXT", "placeholder": "e.g. Nguyen Van..." },
    { "name": "productCode", "label": "Deposit Product", "type": "SELECT", "options": [{ "label": "All Products", "value": "" }, { "label": "Standard Demand Deposit VND (CASA_VND)", "value": "CASA_VND" }] },
    { "name": "status", "label": "Account Status", "type": "SELECT", "options": [{ "label": "All Statuses", "value": "" }, { "label": "Active", "value": "ACTIVE" }, { "label": "Dormant", "value": "DORMANT" }, { "label": "Frozen", "value": "FROZEN" }, { "label": "Closed", "value": "CLOSED" }] },
    { "name": "branchCode", "label": "Branch Code", "type": "TEXT", "placeholder": "e.g. 001..." }
  ],') || TO_CLOB('
  "columns": [
    { "key": "accountNumber", "label": "Account Number", "type": "MONO_BOLD" },
    { "key": "customerName", "label": "Account Holder", "type": "TEXT" },
    { "key": "availableBalance", "label": "Available Balance", "type": "CURRENCY", "currencyKey": "currency" },
    { "key": "ledgerBalance", "label": "Ledger Balance", "type": "CURRENCY", "currencyKey": "currency" },
    { "key": "productCode", "label": "Product", "type": "BADGE" },
    { "key": "branchCode", "label": "Branch", "type": "TEXT" },
    { "key": "status", "label": "Status", "type": "STATUS_BADGE" }
  ],
  "rowActions": [
    {
      "code": "ACT_DEP",
      "label": "Deposit",
      "style": "emerald",
      "title": "Deposit Cash into this account",
      "targetFormCode": "FORM_CASH_DEP",
      "paramsMapping": { "accountNumber": "accountNumber", "accountHolderName": "customerName" }
    },
    {
      "code": "ACT_WDR",
      "label": "Withdraw",
      "style": "amber",
      "title": "Withdraw Cash from this account",
      "targetFormCode": "FORM_CASH_WDR",
      "paramsMapping": { "accountNumber": "accountNumber", "accountHolderName": "customerName" }
    },
    {
      "code": "ACT_TRF",
      "label": "Transfer",
      "style": "blue",
      "title": "Transfer from this account",
      "targetFormCode": "FORM_TRF_INTERNAL",
      "paramsMapping": { "fromAccountNumber": "accountNumber", "sourceAccountNumber": "accountNumber" }
    }
  ]
}')
WHERE form_code = 'FORM_DPT_INFO';
