-- ============================================================================
-- V14: Create FORM_SHORTCUT table, Seed Extended Forms & Shortcuts
-- ============================================================================

-- 1. Create FORM_SHORTCUT table
CREATE TABLE FORM_SHORTCUT (
    shortcut_code       VARCHAR2(50)    PRIMARY KEY,
    form_code           VARCHAR2(50)    NOT NULL REFERENCES FORM(form_code),
    form_name           VARCHAR2(150)   NOT NULL,
    category            VARCHAR2(50)    NOT NULL, -- 'FO_TRANSACTION', 'BO_CATALOGUE', 'INQUIRY'
    module              VARCHAR2(20)    NOT NULL, -- 'DPT', 'CRD', 'CUST', 'GL'
    keywords            VARCHAR2(255)   NOT NULL,
    icon_name           VARCHAR2(50)    DEFAULT 'FileText' NOT NULL,
    display_order       NUMBER(4)       DEFAULT 1 NOT NULL,
    is_active           CHAR(1)         DEFAULT 'Y' NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL
);

CREATE INDEX IDX_FORM_SHORTCUT_MODULE ON FORM_SHORTCUT(module, is_active);
CREATE INDEX IDX_FORM_SHORTCUT_CAT ON FORM_SHORTCUT(category, is_active);

-- 2. Add / Update Form definitions in FORM

-- Form: FORM_CREDIT_ACCOUNT_OPEN (Replaces/Aliases FORM_LOAN_ORIGINATION)
MERGE INTO FORM dest
USING (SELECT 'FORM_CREDIT_ACCOUNT_OPEN' AS form_code FROM DUAL) src
ON (dest.form_code = src.form_code)
WHEN MATCHED THEN
    UPDATE SET 
        trx_code = 'LOAN_CREATE',
        module = 'CRD',
        form_title = 'Open Credit Account (LOAN_CREATE)',
        version = 1,
        is_active = 'Y',
        schema_payload = '{
            "formCode": "FORM_CREDIT_ACCOUNT_OPEN",
            "trxCode": "LOAN_CREATE",
            "title": "Open Credit Account (LOAN_CREATE)",
            "submitEndpoint": "/api/v1/lending/contracts",
            "fields": [
                {
                    "name": "cifNumber",
                    "label": "Customer CIF Number",
                    "type": "TEXT",
                    "required": true,
                    "placeholder": "e.g. CIF-10000001...",
                    "steps": ["LOOKUP_CUSTOMER"]
                },
                {
                    "name": "customerName",
                    "label": "Customer Name",
                    "type": "TEXT",
                    "readOnly": true
                },
                {
                    "name": "linkedCasaAccountNumber",
                    "label": "Disbursement & Repayment CASA Account",
                    "type": "TEXT",
                    "required": true,
                    "placeholder": "Enter 16-digit CASA account...",
                    "steps": ["LOOKUP_LINKED_CASA"]
                },
                {
                    "name": "productCode",
                    "label": "Credit Product Package",
                    "type": "CODELIST_SELECT",
                    "codeListType": "LOAN_PRODUCT",
                    "defaultValue": "LN_CONSUMER_VND",
                    "required": true
                },
                {
                    "name": "principalAmount",
                    "label": "Requested Principal (VND)",
                    "type": "CURRENCY",
                    "required": true,
                    "placeholder": "50,000,000.0000",
                    "steps": ["PREVIEW_LOAN_EMI"]
                },
                {
                    "name": "termMonths",
                    "label": "Loan Term (Months)",
                    "type": "NUMBER",
                    "defaultValue": 12,
                    "required": true,
                    "steps": ["PREVIEW_LOAN_EMI"]
                },
                {
                    "name": "interestMargin",
                    "label": "Agreed Margin Rate (e.g. 0.0250)",
                    "type": "NUMBER",
                    "defaultValue": 0.0250,
                    "steps": ["PREVIEW_LOAN_EMI"]
                },
                {
                    "name": "monthlyEstimatedEmi",
                    "label": "Estimated Monthly Payment (EMI)",
                    "type": "CURRENCY",
                    "readOnly": true
                }
            ],
            "actions": [
                {
                    "code": "BTN_PREVIEW_EMI",
                    "label": "Recalculate EMI",
                    "style": "secondary",
                    "actionType": "WORKFLOW_STEP",
                    "stepCode": "PREVIEW_LOAN_EMI"
                },
                {
                    "code": "BTN_SUBMIT_CONTRACT",
                    "label": "Open Credit Account",
                    "style": "primary",
                    "actionType": "SUBMIT_TRX",
                    "endpoint": "/api/v1/lending/contracts"
                }
            ]
        }'
WHEN NOT MATCHED THEN
    INSERT (form_code, trx_code, module, form_title, version, is_active, schema_payload)
    VALUES (
        'FORM_CREDIT_ACCOUNT_OPEN',
        'LOAN_CREATE',
        'CRD',
        'Open Credit Account (LOAN_CREATE)',
        1,
        'Y',
        '{
            "formCode": "FORM_CREDIT_ACCOUNT_OPEN",
            "trxCode": "LOAN_CREATE",
            "title": "Open Credit Account (LOAN_CREATE)",
            "submitEndpoint": "/api/v1/lending/contracts",
            "fields": [
                {
                    "name": "cifNumber",
                    "label": "Customer CIF Number",
                    "type": "TEXT",
                    "required": true,
                    "placeholder": "e.g. CIF-10000001...",
                    "steps": ["LOOKUP_CUSTOMER"]
                },
                {
                    "name": "customerName",
                    "label": "Customer Name",
                    "type": "TEXT",
                    "readOnly": true
                },
                {
                    "name": "linkedCasaAccountNumber",
                    "label": "Disbursement & Repayment CASA Account",
                    "type": "TEXT",
                    "required": true,
                    "placeholder": "Enter 16-digit CASA account...",
                    "steps": ["LOOKUP_LINKED_CASA"]
                },
                {
                    "name": "productCode",
                    "label": "Credit Product Package",
                    "type": "CODELIST_SELECT",
                    "codeListType": "LOAN_PRODUCT",
                    "defaultValue": "LN_CONSUMER_VND",
                    "required": true
                },
                {
                    "name": "principalAmount",
                    "label": "Requested Principal (VND)",
                    "type": "CURRENCY",
                    "required": true,
                    "placeholder": "50,000,000.0000",
                    "steps": ["PREVIEW_LOAN_EMI"]
                },
                {
                    "name": "termMonths",
                    "label": "Loan Term (Months)",
                    "type": "NUMBER",
                    "defaultValue": 12,
                    "required": true,
                    "steps": ["PREVIEW_LOAN_EMI"]
                },
                {
                    "name": "interestMargin",
                    "label": "Agreed Margin Rate (e.g. 0.0250)",
                    "type": "NUMBER",
                    "defaultValue": 0.0250,
                    "steps": ["PREVIEW_LOAN_EMI"]
                },
                {
                    "name": "monthlyEstimatedEmi",
                    "label": "Estimated Monthly Payment (EMI)",
                    "type": "CURRENCY",
                    "readOnly": true
                }
            ],
            "actions": [
                {
                    "code": "BTN_PREVIEW_EMI",
                    "label": "Recalculate EMI",
                    "style": "secondary",
                    "actionType": "WORKFLOW_STEP",
                    "stepCode": "PREVIEW_LOAN_EMI"
                },
                {
                    "code": "BTN_SUBMIT_CONTRACT",
                    "label": "Open Credit Account",
                    "style": "primary",
                    "actionType": "SUBMIT_TRX",
                    "endpoint": "/api/v1/lending/contracts"
                }
            ]
        }'
    );

-- Form: FORM_ACCOUNT_OPEN (Open CASA Deposit Account)
INSERT INTO FORM (form_code, trx_code, module, form_title, version, is_active, schema_payload)
VALUES (
    'FORM_ACCOUNT_OPEN',
    'OPEN_CASA',
    'DPT',
    'Open Deposit Account (CASA)',
    1,
    'Y',
    '{
        "formCode": "FORM_ACCOUNT_OPEN",
        "trxCode": "OPEN_CASA",
        "title": "Open Deposit Account (CASA)",
        "submitEndpoint": "/api/v1/accounts/open",
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
                "type": "CODELIST_SELECT",
                "codeListType": "DPT_PRODUCT",
                "defaultValue": "CASA_VND",
                "required": true
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
                "endpoint": "/api/v1/accounts/open"
            }
        ]
    }'
);

-- Form: FORM_DPT_INFO (Deposit Account Information)
INSERT INTO FORM (form_code, trx_code, module, form_title, version, is_active, schema_payload)
VALUES (
    'FORM_DPT_INFO',
    'INQ_DPT',
    'DPT',
    'Deposit Account Information',
    1,
    'Y',
    '{
        "formCode": "FORM_DPT_INFO",
        "trxCode": "INQ_DPT",
        "title": "Deposit Account Information Inquiry",
        "fields": [
            {
                "name": "accountNumber",
                "label": "Account Number (16 Digits)",
                "type": "ACCOUNT_LOOKUP",
                "required": true,
                "placeholder": "Enter account number to inspect...",
                "steps": ["LOOKUP_ACCOUNT"]
            },
            {
                "name": "accountHolderName",
                "label": "Account Holder Name",
                "type": "TEXT",
                "readOnly": true
            },
            {
                "name": "accountStatus",
                "label": "Account Status",
                "type": "TEXT",
                "readOnly": true
            },
            {
                "name": "availableBalance",
                "label": "Available Balance",
                "type": "CURRENCY",
                "readOnly": true
            },
            {
                "name": "currency",
                "label": "Currency",
                "type": "TEXT",
                "readOnly": true
            }
        ],
        "actions": [
            {
                "code": "BTN_SEARCH_ACC",
                "label": "Search Account",
                "style": "secondary",
                "actionType": "WORKFLOW_STEP",
                "stepCode": "LOOKUP_ACCOUNT"
            },
            {
                "code": "BTN_GO_DEPOSIT",
                "label": "Deposit Cash",
                "style": "primary",
                "actionType": "NAVIGATE_FORM",
                "targetFormCode": "FORM_CASH_DEP",
                "passParams": ["accountNumber"]
            },
            {
                "code": "BTN_GO_TRANSFER",
                "label": "Transfer From Account",
                "style": "primary",
                "actionType": "NAVIGATE_FORM",
                "targetFormCode": "FORM_TRF_INTERNAL",
                "passParams": ["accountNumber"]
            }
        ]
    }'
);

-- Form: FORM_DPT_CATALOGUE (Deposit Catalogue Definition - BO Form)
INSERT INTO FORM (form_code, trx_code, module, form_title, version, is_active, schema_payload)
VALUES (
    'FORM_DPT_CATALOGUE',
    'BO_DPT_CAT',
    'DPT',
    'Deposit Product Catalogue Definition (BO)',
    1,
    'Y',
    '{
        "formCode": "FORM_DPT_CATALOGUE",
        "trxCode": "BO_DPT_CAT",
        "title": "Deposit Product Catalogue Definition (BO)",
        "fields": [
            {
                "name": "productCode",
                "label": "Product Code",
                "type": "TEXT",
                "required": true,
                "defaultValue": "CASA_VND"
            },
            {
                "name": "productName",
                "label": "Product Display Name",
                "type": "TEXT",
                "required": true,
                "defaultValue": "Standard Demand Deposit VND"
            },
            {
                "name": "currency",
                "label": "Currency",
                "type": "TEXT",
                "defaultValue": "VND",
                "readOnly": true
            },
            {
                "name": "glClassCode",
                "label": "General Ledger Group",
                "type": "TEXT",
                "defaultValue": "4211",
                "readOnly": true
            },
            {
                "name": "baseInterestRate",
                "label": "Base Annual Interest Rate",
                "type": "NUMBER",
                "defaultValue": 0.0020,
                "required": true
            }
        ],
        "actions": [
            {
                "code": "BTN_SAVE_DPT_CAT",
                "label": "Save Catalogue Definition",
                "style": "primary",
                "actionType": "SUBMIT_TRX",
                "endpoint": "/api/v1/admin/catalogue/deposit"
            }
        ]
    }'
);

-- Form: FORM_CRD_INFO (Credit Loan Account Information)
INSERT INTO FORM (form_code, trx_code, module, form_title, version, is_active, schema_payload)
VALUES (
    'FORM_CRD_INFO',
    'INQ_CRD',
    'CRD',
    'Credit Account Information',
    1,
    'Y',
    '{
        "formCode": "FORM_CRD_INFO",
        "trxCode": "INQ_CRD",
        "title": "Credit Account & Loan Schedule Inquiry",
        "fields": [
            {
                "name": "loanId",
                "label": "Loan Contract Number",
                "type": "TEXT",
                "required": true,
                "placeholder": "Enter Loan Contract ID (e.g. 10012619)..."
            },
            {
                "name": "customerName",
                "label": "Borrower Name",
                "type": "TEXT",
                "readOnly": true
            },
            {
                "name": "principalAmount",
                "label": "Approved Principal",
                "type": "CURRENCY",
                "readOnly": true
            },
            {
                "name": "outstandingPrincipal",
                "label": "Outstanding Principal Balance",
                "type": "CURRENCY",
                "readOnly": true
            }
        ],
        "actions": [
            {
                "code": "BTN_INSPECT_LOAN",
                "label": "Inspect Loan & Schedules",
                "style": "secondary",
                "actionType": "WORKFLOW_STEP",
                "stepCode": "INSPECT_LOAN"
            },
            {
                "code": "BTN_OPEN_NEW_CREDIT",
                "label": "+ Open New Credit Account",
                "style": "primary",
                "actionType": "NAVIGATE_FORM",
                "targetFormCode": "FORM_CREDIT_ACCOUNT_OPEN"
            }
        ]
    }'
);

-- Form: FORM_CRD_CATALOGUE (Credit Catalogue Definition - BO Form)
INSERT INTO FORM (form_code, trx_code, module, form_title, version, is_active, schema_payload)
VALUES (
    'FORM_CRD_CATALOGUE',
    'BO_CRD_CAT',
    'CRD',
    'Credit Product Catalogue Definition (BO)',
    1,
    'Y',
    '{
        "formCode": "FORM_CRD_CATALOGUE",
        "trxCode": "BO_CRD_CAT",
        "title": "Credit Product Catalogue Definition (BO)",
        "fields": [
            {
                "name": "productCode",
                "label": "Credit Product Code",
                "type": "TEXT",
                "required": true,
                "defaultValue": "LN_CONSUMER_VND"
            },
            {
                "name": "productName",
                "label": "Product Name",
                "type": "TEXT",
                "required": true,
                "defaultValue": "Consumer Installment Loan VND"
            },
            {
                "name": "baseInterestRate",
                "label": "Base Annual Rate (e.g. 0.0950)",
                "type": "NUMBER",
                "defaultValue": 0.0950,
                "required": true
            },
            {
                "name": "minMarginRate",
                "label": "Minimum Margin Rate",
                "type": "NUMBER",
                "defaultValue": 0.0100,
                "required": true
            },
            {
                "name": "maxMarginRate",
                "label": "Maximum Margin Rate",
                "type": "NUMBER",
                "defaultValue": 0.0500,
                "required": true
            }
        ],
        "actions": [
            {
                "code": "BTN_SAVE_CRD_CAT",
                "label": "Save Credit Catalogue",
                "style": "primary",
                "actionType": "SUBMIT_TRX",
                "endpoint": "/api/v1/admin/catalogue/credit"
            }
        ]
    }'
);

-- Form: FORM_CUST_PROFILE (Unified Customer Profile Inquiry & Action Form)
INSERT INTO FORM (form_code, trx_code, module, form_title, version, is_active, schema_payload)
VALUES (
    'FORM_CUST_PROFILE',
    'CUST_PROFILE',
    'CUST',
    'Customer Profile',
    1,
    'Y',
    '{
        "formCode": "FORM_CUST_PROFILE",
        "trxCode": "CUST_PROFILE",
        "title": "Customer Profile & CIF 360",
        "fields": [
            {
                "name": "cifNumber",
                "label": "Customer CIF Number",
                "type": "TEXT",
                "required": true,
                "placeholder": "Enter CIF number (e.g. CIF-10012612)...",
                "steps": ["LOOKUP_CUSTOMER"]
            },
            {
                "name": "customerName",
                "label": "Full Name",
                "type": "TEXT",
                "readOnly": true
            },
            {
                "name": "nationalId",
                "label": "National ID / Passport",
                "type": "TEXT",
                "readOnly": true
            },
            {
                "name": "customerId",
                "label": "Internal Customer ID",
                "type": "STATIC",
                "readOnly": true
            }
        ],
        "actions": [
            {
                "code": "BTN_SEARCH_CIF",
                "label": "Search CIF",
                "style": "secondary",
                "actionType": "WORKFLOW_STEP",
                "stepCode": "LOOKUP_CUSTOMER"
            },
            {
                "code": "BTN_ADD_CUSTOMER",
                "label": "+ Add New Customer Profile (FO)",
                "style": "primary",
                "actionType": "NAVIGATE_FORM",
                "targetFormCode": "FORM_CUST_CREATE"
            },
            {
                "code": "BTN_OPEN_CASA_FOR_CUST",
                "label": "Open CASA Account",
                "style": "secondary",
                "actionType": "NAVIGATE_FORM",
                "targetFormCode": "FORM_ACCOUNT_OPEN",
                "passParams": ["cifNumber"]
            },
            {
                "code": "BTN_OPEN_LOAN_FOR_CUST",
                "label": "Open Credit Account",
                "style": "secondary",
                "actionType": "NAVIGATE_FORM",
                "targetFormCode": "FORM_CREDIT_ACCOUNT_OPEN",
                "passParams": ["cifNumber"]
            }
        ]
    }'
);

-- Form: FORM_CUST_CREATE (Register New Customer Profile - FO Transaction)
INSERT INTO FORM (form_code, trx_code, module, form_title, version, is_active, schema_payload)
VALUES (
    'FORM_CUST_CREATE',
    'CUST_CREATE',
    'CUST',
    'Register Customer Profile (FO)',
    1,
    'Y',
    '{
        "formCode": "FORM_CUST_CREATE",
        "trxCode": "CUST_CREATE",
        "title": "Register New Customer Profile (CIF)",
        "submitEndpoint": "/api/v1/customers",
        "fields": [
            {
                "name": "fullName",
                "label": "Full Legal Name",
                "type": "TEXT",
                "required": true,
                "placeholder": "e.g. NGUYEN VAN A"
            },
            {
                "name": "nationalId",
                "label": "National ID / Passport Number",
                "type": "TEXT",
                "required": true,
                "placeholder": "e.g. 079099001234"
            },
            {
                "name": "customerType",
                "label": "Customer Type",
                "type": "CODELIST_SELECT",
                "codeListType": "CUSTOMER_TYPE",
                "defaultValue": "INDIVIDUAL",
                "required": true
            },
            {
                "name": "kycStatus",
                "label": "KYC Verification Status",
                "type": "TEXT",
                "defaultValue": "VERIFIED",
                "readOnly": true
            }
        ],
        "actions": [
            {
                "code": "BTN_SUBMIT_CUST",
                "label": "Create Customer Profile",
                "style": "primary",
                "actionType": "SUBMIT_TRX",
                "endpoint": "/api/v1/customers"
            }
        ]
    }'
);

-- 3. Seed FORM_SHORTCUT Table for Spotlight Search & Menu Navigation

-- FO Transactions
INSERT INTO FORM_SHORTCUT (shortcut_code, form_code, form_name, category, module, keywords, icon_name, display_order)
VALUES ('SC_CASH_DEP', 'FORM_CASH_DEP', 'Cash Deposit', 'FO_TRANSACTION', 'DPT', 'cash, deposit, nop tien, nap tien, dpt, casa', 'ArrowDownLeft', 1);

INSERT INTO FORM_SHORTCUT (shortcut_code, form_code, form_name, category, module, keywords, icon_name, display_order)
VALUES ('SC_CASH_WDR', 'FORM_CASH_WDR', 'Cash Withdrawal', 'FO_TRANSACTION', 'DPT', 'cash, withdraw, withdrawal, rut tien, dpt', 'ArrowUpRight', 2);

INSERT INTO FORM_SHORTCUT (shortcut_code, form_code, form_name, category, module, keywords, icon_name, display_order)
VALUES ('SC_TRF_INTERNAL', 'FORM_TRF_INTERNAL', 'Internal Fund Transfer', 'FO_TRANSACTION', 'DPT', 'transfer, internal, chuyen khoan, send money', 'ArrowLeftRight', 3);

INSERT INTO FORM_SHORTCUT (shortcut_code, form_code, form_name, category, module, keywords, icon_name, display_order)
VALUES ('SC_ACCOUNT_OPEN', 'FORM_ACCOUNT_OPEN', 'Open Deposit Account', 'FO_TRANSACTION', 'DPT', 'open account, mo tai khoan, dpt, casa, account open', 'CreditCard', 4);

INSERT INTO FORM_SHORTCUT (shortcut_code, form_code, form_name, category, module, keywords, icon_name, display_order)
VALUES ('SC_CREDIT_ACCOUNT_OPEN', 'FORM_CREDIT_ACCOUNT_OPEN', 'Open Credit Account', 'FO_TRANSACTION', 'CRD', 'credit, loan, vay, contract, open credit account, origination', 'FileText', 5);

INSERT INTO FORM_SHORTCUT (shortcut_code, form_code, form_name, category, module, keywords, icon_name, display_order)
VALUES ('SC_CUST_CREATE', 'FORM_CUST_CREATE', 'Register Customer Profile', 'FO_TRANSACTION', 'CUST', 'customer, cif, create customer, register, add customer, profile', 'UserPlus', 6);

-- Deposit Services
INSERT INTO FORM_SHORTCUT (shortcut_code, form_code, form_name, category, module, keywords, icon_name, display_order)
VALUES ('SC_DPT_INFO', 'FORM_DPT_INFO', 'Deposit Account Information', 'INQUIRY', 'DPT', 'deposit, info, balance, account, tra cuu tai khoan', 'Search', 10);

INSERT INTO FORM_SHORTCUT (shortcut_code, form_code, form_name, category, module, keywords, icon_name, display_order)
VALUES ('SC_DPT_CATALOGUE', 'FORM_DPT_CATALOGUE', 'Deposit Catalogue Definition (BO)', 'BO_CATALOGUE', 'DPT', 'catalogue, deposit product, bo, back office, interest rate', 'Settings', 11);

-- Credit Services
INSERT INTO FORM_SHORTCUT (shortcut_code, form_code, form_name, category, module, keywords, icon_name, display_order)
VALUES ('SC_CRD_INFO', 'FORM_CRD_INFO', 'Credit Account Information', 'INQUIRY', 'CRD', 'credit, loan info, schedule, amortization, tra cuu khoan vay', 'Search', 20);

INSERT INTO FORM_SHORTCUT (shortcut_code, form_code, form_name, category, module, keywords, icon_name, display_order)
VALUES ('SC_CRD_CATALOGUE', 'FORM_CRD_CATALOGUE', 'Credit Catalogue Definition (BO)', 'BO_CATALOGUE', 'CRD', 'credit catalogue, bo, margin, base rate, back office', 'Settings', 21);

-- Customer Services
INSERT INTO FORM_SHORTCUT (shortcut_code, form_code, form_name, category, module, keywords, icon_name, display_order)
VALUES ('SC_CUST_PROFILE', 'FORM_CUST_PROFILE', 'Customer Profile', 'INQUIRY', 'CUST', 'customer, profile, cif, kyc, customer information, ho so khach hang', 'User', 30);
